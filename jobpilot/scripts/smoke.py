#!/usr/bin/env python3
"""Exercise JobPilot's real REST workflow using synthetic data; disposable DB only."""
from __future__ import annotations

import argparse
import io
import json
import time
import urllib.error
import urllib.request
import uuid
import zipfile
from dataclasses import dataclass
from typing import Any


@dataclass
class Client:
    base: str
    checks: int = 0

    def request(self, method: str, path: str, body: Any = None,
                *, expected: int | tuple[int, ...] = 200,
                content_type: str = 'application/json',
                extra_headers: dict[str, str] | None = None) -> Any:
        payload = body if isinstance(body, bytes) else json.dumps(body).encode() if body is not None else None
        headers = {'Accept': 'application/json', **(extra_headers or {})}
        if payload is not None:
            headers['Content-Type'] = content_type
        request = urllib.request.Request(self.base + path, data=payload, headers=headers, method=method)
        try:
            response = urllib.request.urlopen(request, timeout=60)
        except urllib.error.HTTPError as error:
            response = error
        raw = response.read()
        wanted = (expected,) if isinstance(expected, int) else expected
        if response.status not in wanted:
            raise AssertionError(f'{method} {path}: HTTP {response.status}, expected {wanted}: {raw[:500].decode(errors="replace")}')
        if 'json' in response.headers.get('Content-Type', '') and raw:
            return json.loads(raw)
        return raw

    def check(self, condition: bool, label: str) -> None:
        if not condition:
            raise AssertionError(label)
        self.checks += 1
        print(f'PASS {self.checks:02d} {label}', flush=True)

    def task(self, response: dict[str, Any]) -> dict[str, Any]:
        task_id = response.get('taskId', response.get('id'))
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            task = self.request('GET', f'/api/tasks/{task_id}')
            status = task.get('status', '')
            if status in ('COMPLETED', 'SUCCEEDED', 'DONE'):
                return task
            if status == 'FAILED':
                raise AssertionError(f'Async task failed: {task}')
            time.sleep(.25)
        raise AssertionError(f'Async task timed out: {task_id}')


def synthetic_docx() -> bytes:
    text = ('Synthetic Test Candidate', 'Customer Operations Specialist',
            'Reviewed customer escalations and collaborated with support teams.',
            'Skills: customer support, communication, issue resolution',
            'Languages: English')
    document = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>'
    document += ''.join('<w:p><w:r><w:t>' + line + '</w:t></w:r></w:p>' for line in text)
    document += '<w:sectPr/></w:body></w:document>'
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('[Content_Types].xml', '<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
        archive.writestr('_rels/.rels', '<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
        archive.writestr('word/document.xml', document)
    return output.getvalue()


def multipart(file: bytes) -> tuple[bytes, str]:
    boundary = 'jobpilot-' + uuid.uuid4().hex
    payload = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="synthetic.docx"\r\n'
               'Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document\r\n\r\n').encode()
    return payload + file + f'\r\n--{boundary}--\r\n'.encode(), f'multipart/form-data; boundary={boundary}'


def run(client: Client) -> None:
    health = client.request('GET', '/actuator/health')
    client.check(health.get('status') == 'UP', 'backend and database health')
    client.request('GET', '/api/candidates/current', expected=403,
                   extra_headers={'Host': 'attacker.invalid'})
    client.request('PUT', '/api/candidates/current', {'name': 'hostile write'}, expected=403,
                   extra_headers={'Origin': 'https://attacker.invalid'})
    client.request('GET', '/api/candidates/current', expected=403,
                   extra_headers={'Sec-Fetch-Site': 'cross-site'})
    client.check(True, 'hostile Host, cross-site Origin writes and cross-site reads are rejected')
    settings = client.request('GET', '/api/settings')
    client.check(settings['integration']['authorizedDiscovery'] is False,
                 'integration honestly reports manual-only LinkedIn access')
    client.request('PUT', '/api/settings', {
        'ai': {'provider': 'DISABLED', 'model': 'synthetic-test',
               'temperature': .1, 'externalConsent': False}})
    client.request('PUT', '/api/settings', {
        'ai': {'provider': 'OPENAI_COMPATIBLE', 'model': 'test',
               'temperature': .1, 'externalConsent': False}}, expected=400)
    client.check(True, 'external AI requires explicit consent')

    # This smoke replaces the canonical profile only after --allow-write.
    client.request('PUT', '/api/candidates/current', {'name': '', 'summary': '', 'verifiedFacts': [], 'pendingFacts': []})
    original = synthetic_docx()
    payload, content_type = multipart(original)
    candidate = client.request('POST', '/api/candidates/cv', payload,
                               content_type=content_type)
    client.check(bool(candidate['pendingFacts']), 'CV upload extracts facts awaiting confirmation')
    client.check(not candidate['verifiedFacts'], 'extracted claims are not automatically verified')
    document = candidate['documents'][-1]
    restored = client.request('GET', f"/api/candidates/documents/{document['documentId']}/download")
    client.check(restored == original, 'original CV is preserved byte for byte')
    verified = {
        'name': 'Synthetic Test Candidate',
        'summary': 'Customer operations specialist.',
        'verifiedFacts': [
            {'id': 'smoke-exp', 'section': 'Experience',
             'text': 'Resolved customer support escalations and collaborated with support teams.'},
            {'id': 'smoke-skills', 'section': 'Skills',
             'text': 'Customer support, communication, issue resolution.'},
            {'id': 'smoke-course', 'section': 'Courses',
             'text': 'Project Management course, currently studying; not a certification.'}],
        'pendingFacts': candidate['pendingFacts']}
    candidate = client.request('PUT', '/api/candidates/current', verified)
    candidate_reload = client.request('GET', '/api/candidates/current')
    client.check(candidate_reload['verifiedFacts'] == verified['verifiedFacts'],
                 'user-approved candidate facts persist')

    preferences = client.request('GET', '/api/search-preferences')
    client.check(preferences['salaryFilterEnabled'] is False and
                 preferences['includeUndisclosedSalaries'] is True,
                 'salary filtering defaults off with undisclosed salaries included')
    client.check(len(preferences['titles']) == 12 and preferences['publicationDays'] == 30,
                 'default title and publication-window preferences')
    links = client.request('GET', '/api/jobs/search-links')
    client.check(bool(links) and all('linkedin.com/jobs/search' in link['url'] for link in links),
                 'generated discovery links use LinkedIn only')
    search = client.request('POST', '/api/jobs/search')
    client.check(search['execution']['jobCount'] == 0 and search['status'] == 'MANUAL_ONLY',
                 'search history never invents automatically discovered jobs')
    client.request('POST', '/api/jobs/import', {'url': 'https://linkedin.com.evil.invalid/jobs/view/123'}, expected=400)
    client.request('POST', '/api/jobs/import', {'url': 'http://127.0.0.1:8000/secret'}, expected=400)
    client.check(True, 'manual import rejects non-LinkedIn URLs and local network targets')
    linkedin_id = str(uuid.uuid4().int)[:13]
    job_input = {
        'url': f'https://www.linkedin.com/jobs/view/{linkedin_id}/?trackingId=smoke',
        'title': 'Customer Operations Specialist', 'company': 'Synthetic Operations Company',
        'location': 'Kraków, Poland', 'workArrangement': 'HYBRID',
        'employmentType': 'FULL_TIME', 'seniority': 'MID_SENIOR',
        'description': 'Resolve customer support escalations. Collaborate with support teams. '
                       'Strong communication and issue resolution skills required. '
                       'A professional qualification is preferred; do not infer candidate certifications.'}
    job = client.request('POST', '/api/jobs/import', job_input)
    job_id = job['id']
    client.check(job['availability'] == 'UNKNOWN' and job['lastVerifiedAt'] is None,
                 'manual import does not claim verified active availability')
    client.check(job['salarySource'] == 'UNDISCLOSED', 'import never invents salary')
    duplicate = client.request('POST', '/api/jobs/import', {
        **job_input, 'url': f'https://www.linkedin.com/jobs/view/{linkedin_id}/?refId=other'})
    client.check(duplicate['id'] == job_id, 'LinkedIn job ID deduplication ignores tracking parameters')
    low_pay = client.request('POST', '/api/jobs/import', {
        **job_input, 'url': f'https://www.linkedin.com/jobs/view/{int(linkedin_id) + 1}/',
        'title': 'Synthetic Low Salary', 'salaryMin': 4000, 'salaryMax': 5000,
        'currency': 'PLN', 'salaryPeriod': 'MONTHLY'})
    client.check(low_pay['salarySource'] == 'ADVERTISED', 'advertised salary is explicitly classified')
    all_jobs = client.request('GET', '/api/jobs?size=100')['items']
    client.check({job_id, low_pay['id']}.issubset({j['id'] for j in all_jobs}),
                 'disabled salary filter retains advertised low salary and undisclosed jobs')
    client.request('PUT', '/api/search-preferences', {
        **preferences, 'salaryFilterEnabled': True, 'salaryMin': 15000,
        'includeUndisclosedSalaries': True})
    filtered = client.request('GET', '/api/jobs?size=100')['items']
    client.check(job_id in {j['id'] for j in filtered} and low_pay['id'] not in {j['id'] for j in filtered},
                 'enabled salary filter retains undisclosed jobs and excludes verified low salaries')
    client.request('PUT', '/api/search-preferences', {
        **preferences, 'salaryFilterEnabled': True, 'salaryMin': 15000,
        'includeUndisclosedSalaries': False})
    filtered = client.request('GET', '/api/jobs?size=100')['items']
    client.check(job_id not in {j['id'] for j in filtered}, 'undisclosed salaries are excluded only explicitly')
    client.request('PUT', '/api/search-preferences', preferences)
    client.request('PATCH', f'/api/jobs/{job_id}', {'saved': True})
    client.check(job_id in {j['id'] for j in client.request('GET', '/api/jobs?saved=true')['items']},
                 'shortlisted job is persistently saved')
    client.task(client.request('POST', f'/api/jobs/{job_id}/analyze'))
    analysis = client.request('GET', f'/api/jobs/{job_id}/analysis')
    client.check(isinstance(analysis['score'], (int, float)) and 0 <= analysis['score'] <= 100,
                 'matching completes asynchronously with bounded suitability score')
    workflow_checks(client, job_id, verified)


def workflow_checks(client: Client, job_id: str, verified: dict[str, Any]) -> None:
    analysis = client.request('GET', f'/api/jobs/{job_id}/analysis')
    client.check(analysis['engine'] == 'EVIDENCE_HEURISTIC_V1' and
                 analysis['salaryAlignment']['status'] == 'NOT_DISCLOSED',
                 'analysis identifies heuristic engine and keeps salary alignment separate')
    fact_ids = {fact['id'] for fact in verified['verifiedFacts']}
    client.check(all(evidence['factId'] in fact_ids for evidence in analysis['evidence']),
                 'matching claims cite only verified candidate facts')
    old_score = analysis['score']
    client.request('PATCH', f'/api/jobs/{job_id}', {'salaryMin': 20000, 'salaryMax': 25000})
    client.task(client.request('POST', f'/api/jobs/{job_id}/analyze'))
    salary_analysis = client.request('GET', f'/api/jobs/{job_id}/analysis')
    client.check(salary_analysis['score'] == old_score and
                 salary_analysis['salaryAlignment']['status'] == 'POTENTIALLY_ALIGNED',
                 'changing advertised compensation never changes suitability score')

    research = client.request('POST', f'/api/jobs/{job_id}/recruiter-research')
    client.check(research['status'] == 'MANUAL_ONLY' and
                 'linkedin.com/search/results/people' in research['peopleSearchUrl'],
                 'recruiter research reports evidence limitations and offers LinkedIn people search')
    recruiter_input = {
        'name': 'Synthetic Recruiter', 'position': 'Talent Acquisition Specialist',
        'company': 'Synthetic Operations Company', 'confidence': 'VERIFIED',
        'profileUrl': 'https://www.linkedin.com/in/synthetic-test-recruiter/',
        'evidenceUrl': client.request('GET', f'/api/jobs/{job_id}')['url']}
    client.request('POST', f'/api/jobs/{job_id}/recruiters', recruiter_input, expected=400)
    client.check(True, 'verified recruiter association requires explicit evidence confirmation')
    recruiter = client.request('POST', f'/api/jobs/{job_id}/recruiters', {
        **recruiter_input, 'confirmedAssociation': True})
    recruiter_list = client.request('GET', f'/api/jobs/{job_id}/recruiters')
    client.check(any(item['id'] == recruiter['id'] and item['evidenceUrl'] and
                     item['verifiedAt'] and item['confidence'] == 'VERIFIED'
                     for item in recruiter_list), 'recruiter confidence and evidence persist')

    generated_task = client.task(client.request('POST', f'/api/jobs/{job_id}/tailored-cv'))
    cv = generated_task['result']
    client.check(cv['status'] == 'DRAFT' and cv['engine'] == 'VERIFIED_FACTS_ONLY',
                 'CV generation produces a reviewable draft from verified facts only')
    client.check(set(cv['factIds']) == fact_ids and
                 all(fact['text'] in cv['preview'] for fact in verified['verifiedFacts']),
                 'CV preview preserves verified factual claims without invented qualifications')
    client.request('GET', f"/api/cvs/{cv['id']}/download?format=pdf", expected=409)
    client.request('GET', f"/api/cvs/{cv['id']}/download?format=docx", expected=409)
    client.check(True, 'both final document exports are blocked until user approval')
    client.request('PUT', f"/api/cvs/{cv['id']}", {'factIds': ['invented-fact']}, expected=400)
    client.check(True, 'CV revisions reject facts outside the verified snapshot')
    approved = client.request('POST', f"/api/cvs/{cv['id']}/approve")
    client.check(approved['status'] == 'APPROVED' and bool(approved['approvedAt']),
                 'CV approval is explicitly recorded')
    pdf = client.request('GET', f"/api/cvs/{cv['id']}/download?format=pdf")
    docx = client.request('GET', f"/api/cvs/{cv['id']}/download?format=docx")
    client.check(pdf.startswith(b'%PDF-') and b'%%EOF' in pdf[-1024:], 'approved PDF has a valid document envelope')
    with zipfile.ZipFile(io.BytesIO(docx)) as archive:
        client.check(archive.testzip() is None and 'word/document.xml' in archive.namelist(),
                     'approved DOCX is a valid Office archive')
        document_xml = archive.read('word/document.xml').decode()
        client.check('Synthetic Test Candidate' in document_xml and
                     all(fact['text'] in document_xml for fact in verified['verifiedFacts']),
                     'downloaded DOCX contains the reviewed candidate facts')
    client.check(any(version['id'] == cv['id'] and version['status'] == 'APPROVED'
                     for version in client.request('GET', f'/api/jobs/{job_id}/cv-versions')),
                 'approved CV version metadata survives a reload')

    client.task(client.request('POST', f'/api/jobs/{job_id}/messages/generate', {
        'tone': 'Professional', 'personalNote': 'Synthetic user-written note.'}))
    messages = client.request('GET', f'/api/jobs/{job_id}/messages')
    client.check({'CONNECTION', 'LINKEDIN', 'EMAIL', 'FOLLOW_UP'}.issubset({m['type'] for m in messages})
                 and all(m['status'] == 'DRAFT' for m in messages),
                 'all four outreach formats are saved as drafts only')
    client.check(all('Synthetic Recruiter' in m['text'] for m in messages),
                 'outreach uses the explicitly verified recruiter identity')
    message = messages[0]
    edited = client.request('PATCH', f"/api/messages/{message['id']}", {'text': 'Synthetic user-reviewed draft.'})
    client.check(edited['text'] == 'Synthetic user-reviewed draft.' and edited['editedByUser'],
                 'outreach drafts support persistent user editing')

    application = client.request('POST', '/api/applications', {
        'jobId': job_id, 'status': 'CV_PREPARED', 'notes': 'Synthetic application note.'})
    app_id = application['id']
    client.request('PATCH', f'/api/applications/{app_id}', {'status': 'APPLIED'}, expected=400)
    client.check(True, 'application cannot be marked applied without explicit user confirmation')
    applied = client.request('PATCH', f'/api/applications/{app_id}', {
        'status': 'APPLIED', 'confirmApplied': True, 'recruiterId': recruiter['id']})
    client.check(applied['status'] == 'APPLIED', 'user-confirmed application status persists')
    client.request('PATCH', f'/api/applications/{app_id}', {
        'status': 'INTERVIEW', 'interviewDate': '2030-01-15T10:00:00Z',
        'interviewNotes': 'Synthetic interview details.', 'followUpDate': '2030-01-16'})
    applications = client.request('GET', '/api/applications')
    client.check(any(a['id'] == app_id and a['status'] == 'INTERVIEW' and a['followUpDate'] == '2030-01-16'
                     for a in applications), 'application notes, interview and follow-up survive reload')
    events = client.request('GET', f'/api/applications/{app_id}/events')
    client.check({'CV_PREPARED', 'APPLIED', 'INTERVIEW'}.issubset({e['status'] for e in events}) and
                 all(e['source'] == 'USER_ACTION' for e in events),
                 'application status history records explicit user actions')
    dashboard = client.request('GET', '/api/dashboard')
    client.check(dashboard['totalJobs'] >= 2 and dashboard['tailoredCvs'] >= 1 and
                 dashboard['applicationsSubmitted'] >= 1 and dashboard['interviewsScheduled'] >= 1,
                 'dashboard aggregates real persisted jobs, documents and application activity')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:8080')
    parser.add_argument('--allow-write', action='store_true', help='Confirm target database is disposable; test replaces current candidate/preferences')
    args = parser.parse_args()
    if not args.allow_write:
        parser.error('Pass --allow-write only for a disposable test database')
    client = Client(args.base_url.rstrip('/'))
    run(client)
    print(f'All {client.checks} cross-service checks passed.')


if __name__ == '__main__':
    main()
