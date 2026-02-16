from worker.curation.models import CurationCandidate


def test_curation_candidate_defaults():
    c = CurationCandidate(
        source="youtube",
        url="https://youtube.com/watch?v=abc",
        title="Test video",
        description="A test",
        author="testuser",
    )
    assert c.source == "youtube"
    assert c.keyword_score == 0.0
    assert c.haiku_pass is None
    assert c.metadata == {}


def test_curation_candidate_with_scores():
    c = CurationCandidate(
        source="google_news",
        url="https://example.com/article",
        title="Open source CLI tool",
        description="A new terminal tool",
        author="blogger",
        keyword_score=0.7,
        haiku_pass=True,
        gemini_score=8.5,
    )
    assert c.keyword_score == 0.7
    assert c.haiku_pass is True
    assert c.gemini_score == 8.5
