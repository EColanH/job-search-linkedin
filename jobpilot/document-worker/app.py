"""Local, bounded document processing. No network requests or AI execution."""
from __future__ import annotations
import json
import os
import re
import shutil
import uuid
from collections import OrderedDict
from io import BytesIO
from pathlib import Path
from typing import Literal
from xml.sax.saxutils import escape
from zipfile import ZipFile

import fitz
from docx import Document
from docx.shared import Inches, Pt
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import FileResponse
from pydantic import BaseModel, ConfigDict, Field, field_validator
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.lib.enums import TA_LEFT
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer

MAX_BYTES = 10 * 1024 * 1024
MAX_UNPACKED = 30 * 1024 * 1024
MAX_TEXT = 150_000
STORE = Path(os.environ.get("DOCUMENT_STORAGE", "./data"))
app = FastAPI(title="JobPilot Document Worker", version="1.0.0")


class Fact(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: str = Field(min_length=1, max_length=100)
    section: str = Field(default="Experience", min_length=1, max_length=100)
    text: str = Field(min_length=1, max_length=5000)

    @field_validator("text", "section")
    @classmethod
    def clean(cls, value: str) -> str:
        value = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", "", value).strip()
        if not value:
            raise ValueError("Text cannot be blank")
        return value


class Profile(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    verified: Literal[True]
    verifiedFacts: list[Fact] = Field(min_length=1, max_length=150)


class Section(BaseModel):
    heading: str = Field(min_length=1, max_length=100)
    text: str = Field(min_length=1, max_length=5000)


class GenerateRequest(BaseModel):
    profile: Profile
    sections: list[Section] | None = Field(default=None, max_length=150)
    title: str = Field(default="Curriculum Vitae", max_length=200)


def folder(document_id: str) -> Path:
    try:
        normalized = str(uuid.UUID(document_id))
    except ValueError:
        raise HTTPException(404, "Document not found")
    if document_id != normalized:
        raise HTTPException(404, "Document not found")
    return STORE / normalized


def docx_text(data: bytes) -> str:
    try:
        with ZipFile(BytesIO(data)) as archive:
            infos = archive.infolist()
            if len(infos) > 2000 or sum(i.file_size for i in infos) > MAX_UNPACKED:
                raise HTTPException(413, "DOCX expands beyond the safe processing limit")
            names = {i.filename for i in infos}
            if "word/document.xml" not in names or "[Content_Types].xml" not in names:
                raise HTTPException(422, "Not a valid DOCX document")
            if any("vbaproject" in n.lower() for n in names):
                raise HTTPException(422, "Macro-enabled documents are not supported")
        document = Document(BytesIO(data))
        paragraphs = [p.text for p in document.paragraphs]
        for table in document.tables:
            for row in table.rows:
                paragraphs.append(" | ".join(cell.text for cell in row.cells))
        return "\n".join(paragraphs)
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(422, "The DOCX document could not be read")


def pdf_text(data: bytes) -> str:
    try:
        with fitz.open(stream=data, filetype="pdf") as document:
            if document.needs_pass:
                raise HTTPException(422, "Password-protected PDFs are not supported")
            if document.page_count > 80:
                raise HTTPException(413, "CVs must contain no more than 80 pages")
            result = []
            for page in document:
                result.append(page.get_text())
                if sum(map(len, result)) > MAX_TEXT:
                    raise HTTPException(413, "Document text exceeds the safe processing limit")
            return "\n".join(result)
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(422, "The PDF document could not be read")


@app.get("/health")
def health():
    STORE.mkdir(parents=True, exist_ok=True)
    return {"status": "ok"}


@app.post("/parse")
async def parse(file: UploadFile = File(...)):
    filename = (file.filename or "cv").replace("\\", "/").rsplit("/", 1)[-1][:240]
    suffix = Path(filename).suffix.lower()
    if suffix not in {".pdf", ".docx"}:
        raise HTTPException(415, "Only PDF and DOCX files are accepted")
    data = await file.read(MAX_BYTES + 1)
    if not data or len(data) > MAX_BYTES:
        raise HTTPException(413, "Upload must be between 1 byte and 10 MB")
    if suffix == ".pdf" and not data.startswith(b"%PDF-"):
        raise HTTPException(422, "File contents do not match PDF format")
    text = pdf_text(data) if suffix == ".pdf" else docx_text(data)
    text = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", "", text).strip()
    if len(text) > MAX_TEXT:
        raise HTTPException(413, "Document text exceeds the safe processing limit")
    if not text:
        raise HTTPException(422, "No selectable text found. Use a text-based PDF or DOCX; OCR is not enabled.")
    # Literal extraction only: an uploaded paragraph is never a verified candidate fact.
    paragraphs = [p.strip() for p in text.splitlines() if p.strip()]
    pending = [{"id": str(uuid.uuid4()), "section": "Imported CV", "text": p[:5000]} for p in paragraphs[:150]]
    document_id = str(uuid.uuid4())
    target = folder(document_id)
    target.mkdir(parents=True, mode=0o700)
    (target / ("original" + suffix)).write_bytes(data)
    (target / "metadata.json").write_text(json.dumps({"originalName": filename, "extension": suffix}), encoding="utf-8")
    (target / "preview.txt").write_text(text, encoding="utf-8")
    warnings = ["Review each extracted paragraph, assign its section, and verify it before use. No facts have been approved."]
    if len(paragraphs) > 150:
        warnings.append("Only the first 150 paragraphs are proposed; the complete original is preserved.")
    return {"documentId": document_id, "originalName": filename, "text": text, "profile": {"name": "", "verified": False, "verifiedFacts": [], "pendingFacts": pending}, "pendingFacts": pending, "warnings": warnings}


def register_font() -> str:
    # DejaVu covers Polish and common CV characters and is embedded into PDF output.
    for candidate in [os.environ.get("DOCUMENT_FONT", ""), "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/Library/Fonts/Arial Unicode.ttf"]:
        if candidate and Path(candidate).is_file():
            if "JobPilotSans" not in pdfmetrics.getRegisteredFontNames():
                pdfmetrics.registerFont(TTFont("JobPilotSans", candidate))
            return "JobPilotSans"
    return "Helvetica"


def validate_outputs(target: Path, expected_text: str):
    pdf = pdf_text((target / "cv.pdf").read_bytes())
    docx = docx_text((target / "cv.docx").read_bytes())
    normalize = lambda s: re.sub(r"\s+", "", s)
    if normalize(expected_text) not in normalize(pdf) or normalize(expected_text) not in normalize(docx):
        raise HTTPException(500, "Generated document text failed validation")


@app.post("/generate")
def generate(request: GenerateRequest):
    verified = {(fact.section, fact.text) for fact in request.profile.verifiedFacts}
    sections = request.sections or [Section(heading=f.section, text=f.text) for f in request.profile.verifiedFacts]
    # A generation request can select and reorder evidence, but cannot smuggle in invented claims.
    if any((section.heading, section.text) not in verified for section in sections):
        raise HTTPException(422, "CV sections must exactly match verified facts, including their headings")
    grouped: OrderedDict[str, list[str]] = OrderedDict()
    for section in sections:
        grouped.setdefault(section.heading, []).append(section.text)
    name = request.profile.name.strip()
    if not name:
        raise HTTPException(422, "Candidate name cannot be blank")
    document_id = str(uuid.uuid4())
    target = folder(document_id)
    target.mkdir(parents=True, mode=0o700)
    preview_parts = [name]
    for heading, paragraphs in grouped.items():
        preview_parts.extend([heading, *paragraphs])
    preview = "\n\n".join(preview_parts)
    try:
        doc = Document()
        section = doc.sections[0]
        section.top_margin = section.bottom_margin = Inches(.65)
        section.left_margin = section.right_margin = Inches(.75)
        doc.styles["Normal"].font.name = "Arial"
        doc.styles["Normal"].font.size = Pt(10)
        doc.add_heading(name, 0)
        for heading, paragraphs in grouped.items():
            doc.add_heading(heading, level=1)
            for paragraph in paragraphs:
                doc.add_paragraph(paragraph)
        doc.save(target / "cv.docx")
        styles = getSampleStyleSheet()
        font = register_font()
        for style in styles.byName.values():
            style.fontName = font
            style.alignment = TA_LEFT
            style.textColor = colors.HexColor("#17243b")
        styles["Normal"].fontSize = 10
        styles["Normal"].leading = 14
        pdf_story = [Paragraph(escape(name), styles["Title"]), Spacer(1, 12)]
        for heading, paragraphs in grouped.items():
            pdf_story.append(Paragraph(escape(heading), styles["Heading2"]))
            for paragraph in paragraphs:
                pdf_story.append(Paragraph(escape(paragraph).replace("\n", "<br/>"), styles["Normal"]))
                pdf_story.append(Spacer(1, 7))
        SimpleDocTemplate(str(target / "cv.pdf"), pagesize=A4, topMargin=40, bottomMargin=40, leftMargin=48, rightMargin=48).build(pdf_story)
        validate_outputs(target, name)
        # Validate every included claim was retained, allowing line wrapping only.
        for section in sections:
            validate_outputs(target, section.text)
        (target / "preview.txt").write_text(preview, encoding="utf-8")
        (target / "metadata.json").write_text(json.dumps({"generated": True}), encoding="utf-8")
        return {"documentId": document_id, "preview": preview, "pdfValid": True, "docxValid": True}
    except Exception:
        shutil.rmtree(target, ignore_errors=True)
        raise


@app.get("/documents/{document_id}/{format}")
def download(document_id: str, format: Literal["pdf", "docx", "original", "preview"]):
    target = folder(document_id)
    if not target.is_dir():
        raise HTTPException(404, "Document not found")
    if format == "original":
        metadata = json.loads((target / "metadata.json").read_text(encoding="utf-8"))
        extension = metadata.get("extension")
        if extension not in {".pdf", ".docx"}:
            raise HTTPException(404, "Original not found")
        path = target / ("original" + extension)
        name = metadata["originalName"]
    else:
        path = target / ("preview.txt" if format == "preview" else "cv." + format)
        name = path.name
    if not path.is_file():
        raise HTTPException(404, "Document not found")
    media = "application/pdf" if path.suffix == ".pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document" if path.suffix == ".docx" else "text/plain"
    return FileResponse(path, filename=name, media_type=media)


@app.delete("/documents")
def delete_all():
    # Internal-only service, called by backend's explicit candidate data deletion workflow.
    if STORE.exists():
        for child in STORE.iterdir():
            if child.is_dir() and re.fullmatch(r"[0-9a-f-]{36}", child.name):
                shutil.rmtree(child)
    return {"deleted": True}
