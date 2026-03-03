#!/usr/bin/env bash
set -euo pipefail

MODEL_ID=""
REGION="${AWS_REGION:-ap-southeast-2}"
PROFILE="${AWS_PROFILE:-}"
USE_CASE_FILE=""
WAIT_SECONDS=600
POLL_INTERVAL=10
TASK_ROLE_NAME=""
DENY_POLICY_NAME="SecureHubBedrockDeny"

usage() {
  cat <<'EOF'
Enable Bedrock model access for a foundation model (idempotent workflow).

Usage:
  scripts/bedrock/enable-model-access.sh --model-id <model-id> [options]

Required:
  --model-id <id>                 Bedrock model id (for example anthropic.claude-3-5-sonnet-20240620-v1:0)

Options:
  --region <aws-region>           AWS region (default: AWS_REGION or ap-southeast-2)
  --profile <aws-profile>         AWS profile (default: AWS_PROFILE if set)
  --use-case-file <path>          JSON file for put-use-case-for-model-access (Anthropic first-time prerequisite)
  --task-role-name <role-name>    ECS task role name to remove deny policy from (optional)
  --deny-policy-name <name>       Inline deny policy name to remove (default: SecureHubBedrockDeny)
  --wait-seconds <n>              Max wait for AVAILABLE state (default: 600)
  --poll-interval <n>             Poll interval seconds (default: 10)
  -h, --help                      Show help
EOF
}

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

aws_cmd() {
  local args=()
  [[ -n "${PROFILE}" ]] && args+=(--profile "${PROFILE}")
  [[ -n "${REGION}" ]] && args+=(--region "${REGION}")
  aws "${args[@]}" "$@"
}

require_bedrock_cli() {
  if ! aws_cmd bedrock list-foundation-model-agreement-offers help >/dev/null 2>&1; then
    fail "AWS CLI does not support 'aws bedrock'. Install AWS CLI v2 recent version (>= 2.27 recommended)."
  fi
}

get_agreement_status() {
  aws_cmd bedrock get-foundation-model-availability \
    --model-id "${MODEL_ID}" \
    --query 'agreementAvailability.status' \
    --output text 2>/dev/null || echo "UNKNOWN"
}

remove_deny_policy_if_configured() {
  if [[ -z "${TASK_ROLE_NAME}" ]]; then
    return
  fi

  if aws_cmd iam get-role-policy \
      --role-name "${TASK_ROLE_NAME}" \
      --policy-name "${DENY_POLICY_NAME}" >/dev/null 2>&1; then
    log "Removing inline deny policy '${DENY_POLICY_NAME}' from role '${TASK_ROLE_NAME}'."
    aws_cmd iam delete-role-policy \
      --role-name "${TASK_ROLE_NAME}" \
      --policy-name "${DENY_POLICY_NAME}" >/dev/null
  else
    log "No deny policy '${DENY_POLICY_NAME}' found on role '${TASK_ROLE_NAME}' (nothing to remove)."
  fi
}

put_use_case_if_provided() {
  [[ -z "${USE_CASE_FILE}" ]] && return
  [[ -f "${USE_CASE_FILE}" ]] || fail "use-case file not found: ${USE_CASE_FILE}"

  log "Submitting model-access use-case form: ${USE_CASE_FILE}"
  local out
  if ! out="$(aws_cmd bedrock put-use-case-for-model-access \
      --form-data "fileb://${USE_CASE_FILE}" 2>&1)"; then
    if grep -qiE 'ConflictException|already|ValidationException' <<<"${out}"; then
      log "Use-case appears already submitted. Continuing."
    else
      echo "${out}" >&2
      fail "put-use-case-for-model-access failed."
    fi
  fi
}

request_model_access() {
  local offer_token
  offer_token="$(aws_cmd bedrock list-foundation-model-agreement-offers \
    --model-id "${MODEL_ID}" \
    --offer-type ALL \
    --query 'offers[0].offerToken' \
    --output text)"

  if [[ -z "${offer_token}" || "${offer_token}" == "None" ]]; then
    fail "No agreement offer token found for model: ${MODEL_ID}"
  fi

  log "Requesting foundation model agreement."
  local out
  if ! out="$(aws_cmd bedrock create-foundation-model-agreement \
      --model-id "${MODEL_ID}" \
      --offer-token "${offer_token}" 2>&1)"; then
    if grep -qi 'ConflictException' <<<"${out}"; then
      log "Model agreement already exists (ConflictException). Continuing."
    else
      echo "${out}" >&2
      fail "create-foundation-model-agreement failed."
    fi
  fi
}

wait_until_available() {
  local deadline=$((SECONDS + WAIT_SECONDS))
  while true; do
    local status
    status="$(get_agreement_status)"
    if [[ "${status}" == "AVAILABLE" ]]; then
      log "Model agreement status: AVAILABLE"
      return
    fi

    if ((SECONDS >= deadline)); then
      fail "Timed out waiting for AVAILABLE status (last status: ${status})."
    fi

    log "Current agreement status: ${status}. Waiting ${POLL_INTERVAL}s..."
    sleep "${POLL_INTERVAL}"
  done
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model-id) MODEL_ID="${2:-}"; shift 2 ;;
    --region) REGION="${2:-}"; shift 2 ;;
    --profile) PROFILE="${2:-}"; shift 2 ;;
    --use-case-file) USE_CASE_FILE="${2:-}"; shift 2 ;;
    --task-role-name) TASK_ROLE_NAME="${2:-}"; shift 2 ;;
    --deny-policy-name) DENY_POLICY_NAME="${2:-}"; shift 2 ;;
    --wait-seconds) WAIT_SECONDS="${2:-}"; shift 2 ;;
    --poll-interval) POLL_INTERVAL="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) fail "Unknown argument: $1" ;;
  esac
done

[[ -n "${MODEL_ID}" ]] || { usage; fail "--model-id is required."; }
[[ "${WAIT_SECONDS}" =~ ^[0-9]+$ ]] || fail "--wait-seconds must be a non-negative integer."
[[ "${POLL_INTERVAL}" =~ ^[0-9]+$ ]] || fail "--poll-interval must be a non-negative integer."

require_bedrock_cli
remove_deny_policy_if_configured

initial_status="$(get_agreement_status)"
if [[ "${initial_status}" == "AVAILABLE" ]]; then
  log "Model access is already AVAILABLE for ${MODEL_ID}."
  aws_cmd bedrock get-foundation-model-availability --model-id "${MODEL_ID}"
  exit 0
fi

if [[ -z "${USE_CASE_FILE}" && "${MODEL_ID}" == anthropic.* ]]; then
  log "Model appears to be Anthropic. If first-time setup fails, pass --use-case-file scripts/bedrock/anthropic-use-case.sample.json (edited)."
fi

put_use_case_if_provided
request_model_access
wait_until_available

aws_cmd bedrock get-foundation-model-availability --model-id "${MODEL_ID}"

