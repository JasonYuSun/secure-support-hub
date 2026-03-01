#!/bin/sh
set -eu

BUCKET_NAME="${ATTACHMENTS_BUCKET_NAME:-securehub-attachments-local}"
REGION="${AWS_DEFAULT_REGION:-ap-southeast-2}"

if awslocal s3api head-bucket --bucket "${BUCKET_NAME}" >/dev/null 2>&1; then
  echo "Bucket ${BUCKET_NAME} already exists."
else
  echo "Creating bucket ${BUCKET_NAME} in region ${REGION}..."
  awslocal s3api create-bucket --bucket "${BUCKET_NAME}" --region "${REGION}" >/dev/null
fi

echo "Applying secure defaults to ${BUCKET_NAME}..."
awslocal s3api put-public-access-block \
  --bucket "${BUCKET_NAME}" \
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true >/dev/null

awslocal s3api put-bucket-encryption \
  --bucket "${BUCKET_NAME}" \
  --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}' >/dev/null

echo "LocalStack bucket ready: ${BUCKET_NAME}"
