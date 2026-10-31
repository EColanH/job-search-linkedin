from io import BytesIO
from zipfile import ZipFile, ZIP_DEFLATED

import fitz
import pytest
from docx import Document
from fastapi.testclient import TestClient

import app as worker


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(worker, "STORE", tmp_path)
    return TestClient(worker.app)


def fixture_docx():
    doc = Document()
    doc.add_paragraph("Synthetic Candidate")
    doc.add_paragraph("Resolved customer issues at Example Company, 2022–2024.")
    data = BytesIO()
    doc.save(data)
    return data.getvalue()


def profile():
    return {"name": "Synthetic Candidate", "verified": True, "verifiedFacts": [{"id": "experience-1", "section": "Experience", "text": "Resolved customer issues at Example Company, 2022–2024."}, {"id": "skills-1", "section": "Skills", "text": "Polish, English, customer communication"}]}


def test_upload_docx_requires_review_and_preserves_original(client):
    data = fixture_docx()
    response = client.post("/parse", files={"file": ("../../cv.docx", data)})
    assert response.status_code == 200
    body = response.json()
    assert body["profile"]["verified"] is False
    assert body["profile"]["verifiedFacts"] == []
    assert "Resolved customer issues" in body["pendingFacts"][1]["text"]
    assert body["originalName"] == "cv.docx"
    assert client.get(f"/documents/{body['documentId']}/original").content == data


def test_pdf_upload(client):
    pdf = fitz.open()
    page = pdf.new_page()
    page.insert_text((40, 40), "Synthetic Candidate")
    result = client.post("/parse", files={"file": ("cv.pdf", pdf.tobytes())})
    assert result.status_code == 200
    assert "Synthetic Candidate" in result.json()["text"]


def test_generation_round_trip_and_immutable_versions(client):
    first = client.post("/generate", json={"profile": profile()})
    second = client.post("/generate", json={"profile": profile()})
    assert first.status_code == second.status_code == 200, first.text
    assert first.json()["documentId"] != second.json()["documentId"]
    assert first.json()["pdfValid"] and first.json()["docxValid"]
    document_id = first.json()["documentId"]
    pdf = client.get(f"/documents/{document_id}/pdf")
    docx = client.get(f"/documents/{document_id}/docx")
    assert "customer communication" in worker.pdf_text(pdf.content)
    assert "2022–2024" in worker.docx_text(docx.content)


def test_generation_rejects_unverified_or_invented_claims(client):
    candidate = profile()
    candidate["verified"] = False
    assert client.post("/generate", json={"profile": candidate}).status_code == 422
    payload = {"profile": profile(), "sections": [{"heading": "Experience", "text": "Managed 50 people"}]}
    assert client.post("/generate", json=payload).status_code == 422


def test_malformed_and_unsupported_upload(client):
    assert client.post("/parse", files={"file": ("cv.pdf", b"not pdf")}).status_code == 422
    assert client.post("/parse", files={"file": ("cv.docx", b"not zip")}).status_code == 422
    assert client.post("/parse", files={"file": ("cv.docm", b"not zip")}).status_code == 415
    assert client.post("/parse", files={"file": ("cv.pdf", b"")}).status_code == 413


def test_zip_bomb_and_macro_rejected(client):
    data = BytesIO()
    with ZipFile(data, "w", ZIP_DEFLATED) as archive:
        archive.writestr("word/document.xml", "a" * (worker.MAX_UNPACKED + 1))
        archive.writestr("[Content_Types].xml", "x")
    assert client.post("/parse", files={"file": ("cv.docx", data.getvalue())}).status_code == 413
    data = BytesIO()
    with ZipFile(data, "w") as archive:
        archive.writestr("word/document.xml", "x")
        archive.writestr("[Content_Types].xml", "x")
        archive.writestr("word/vbaProject.bin", "x")
    assert client.post("/parse", files={"file": ("cv.docx", data.getvalue())}).status_code == 422


def test_path_traversal_missing_and_deletion(client):
    assert client.get("/documents/not-a-uuid/original").status_code == 404
    item = client.post("/generate", json={"profile": profile()}).json()
    assert client.delete("/documents").status_code == 200
    assert client.get(f"/documents/{item['documentId']}/pdf").status_code == 404


def test_scanned_pdf_not_silently_accepted(client):
    pdf = fitz.open()
    pdf.new_page()
    result = client.post("/parse", files={"file": ("cv.pdf", pdf.tobytes())})
    assert result.status_code == 422
    assert "OCR" in result.json()["detail"]


def test_polish_unicode_round_trip(client):
    candidate = profile()
    candidate["name"] = "Żaneta Łódzka"
    candidate["verifiedFacts"][0]["text"] = "Zażółć gęślą jaźń. Współpraca z klientami w Krakowie, 2022–2024."
    result = client.post("/generate", json={"profile": candidate})
    assert result.status_code == 200, result.text
    item = result.json()
    text = worker.pdf_text(client.get(f"/documents/{item['documentId']}/pdf").content)
    assert "Żaneta Łódzka" in text
    assert "Zażółć gęślą jaźń" in text


def test_upload_size_and_page_bounds(client, monkeypatch):
    monkeypatch.setattr(worker, "MAX_BYTES", 10)
    assert client.post("/parse", files={"file": ("cv.pdf", b"%PDF-" + b"x" * 10)}).status_code == 413
    monkeypatch.setattr(worker, "MAX_BYTES", 10 * 1024 * 1024)
    pdf = fitz.open()
    for _ in range(81):
        pdf.new_page()
    assert client.post("/parse", files={"file": ("cv.pdf", pdf.tobytes())}).status_code == 413


def test_encrypted_pdf_rejected(client):
    pdf = fitz.open()
    pdf.new_page()
    data = pdf.tobytes(encryption=fitz.PDF_ENCRYPT_AES_256, owner_pw="owner", user_pw="candidate")
    assert client.post("/parse", files={"file": ("cv.pdf", data)}).status_code == 422


def test_fact_selection_and_order_are_preserved(client):
    candidate = profile()
    skills = candidate["verifiedFacts"][1]
    result = client.post("/generate", json={"profile": candidate, "sections": [{"heading": skills["section"], "text": skills["text"]}]})
    assert result.status_code == 200, result.text
    assert "customer communication" in result.json()["preview"]
    assert "Resolved customer issues" not in result.json()["preview"]
