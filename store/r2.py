import os
import logging

import boto3
from botocore.config import Config

log = logging.getLogger("keyjawn-store")

R2_ACCOUNT_ID = os.environ.get("R2_ACCOUNT_ID", "")
R2_ACCESS_KEY = os.environ.get("R2_ACCESS_KEY_ID", "")
R2_SECRET_KEY = os.environ.get("R2_SECRET_ACCESS_KEY", "")
R2_BUCKET = "amditis-tech"

# Presigned URL expiration: 7 days
DOWNLOAD_EXPIRY = 7 * 24 * 60 * 60

GITHUB_RELEASES = "https://github.com/jamditis/keyjawn/releases"


def get_r2_client():
    return boto3.client(
        "s3",
        endpoint_url=f"https://{R2_ACCOUNT_ID}.r2.cloudflarestorage.com",
        aws_access_key_id=R2_ACCESS_KEY,
        aws_secret_access_key=R2_SECRET_KEY,
        config=Config(signature_version="s3v4"),
        region_name="auto",
    )


def generate_signed_url(r2_key: str, expires_in: int = DOWNLOAD_EXPIRY) -> str:
    try:
        client = get_r2_client()
        return client.generate_presigned_url(
            "get_object",
            Params={"Bucket": R2_BUCKET, "Key": r2_key},
            ExpiresIn=expires_in,
        )
    except Exception as e:
        log.error(f"R2 signed URL failed for {r2_key}: {e}")
        return None
