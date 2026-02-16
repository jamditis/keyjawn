"""Data models for the curation pipeline."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional


@dataclass
class CurationCandidate:
    """A piece of content discovered by a source monitor."""
    source: str          # "youtube", "google_news", "twitch"
    url: str
    title: str
    description: str
    author: str
    published: Optional[datetime] = None
    metadata: dict = field(default_factory=dict)

    # Populated by evaluation pipeline stages
    keyword_score: float = 0.0
    haiku_pass: Optional[bool] = None
    haiku_reasoning: str = ""
    gemini_score: float = 0.0
    gemini_analysis: str = ""
    sonnet_pass: Optional[bool] = None
    sonnet_draft: str = ""
    sonnet_reasoning: str = ""
    final_score: float = 0.0
