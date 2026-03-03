#!/usr/bin/env bash
set -euo pipefail

MODEL_ID=""
REGION="${AWS_REGION:-ap-southeast-2}"
PROFILE="${AWS_PROFILE:-}"
TASK_ROLE_NAME=""
DENY_POLICY_NAME="SecureHubBedrockDeny"
SKIP_DELETE_AGREEMENT=0

usage() {
  cat <<'EOF'
Disable Bedrock model usage for dev/demo cost control.

Important:
- Deleting the model agreement alone may not be sufficient, because model access can be re-established by future invocations.
- For reliable cost protection, also attach a deny policy to the ECS task role.

Usage:
  scripts/bedrock/disable-model-access.sh --model-id <model-id> [options]

Required:
  --model-id <id>                 Bedrock model id

Options:
  --region <aws-region>           AWS region (default: AWS_REGION or ap-southeast-2)
  --profile <aws-profile>         AWS profile (default: AWS_PROFILE if set)
  --task-role-name <role-name>    ECS task role name to attach deny policy (recommended)
  --deny-policy-name <name>       Inline deny policy name (default: SecureHubBedrockDeny)
  --skip-delete-agreement         Skip delete-foundation-model-agreement API call
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
  if ! aws_cmd bedrock get-foundation-model-availability help >/dev/null 2>&1; then
    fail "AWS CLI does not support 'aws bedrock'. Install AWS CLI v2 recent version (>= 2.27 recommended)."
  fi
}

attach_deny_policy_if_configured() {
  if [[ -z "${TASK_ROLE_NAME}" ]]; then
    log "No --task-role-name provided. Skipping deny policy attachment."
    log "WARNING: deleting agreement only may not prevent future re-enable by invocation."
    return
  fi

  local policy_file
  policy_file="$(mktemp)"
  cat > "${policy_file}" <<'JSON'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "DenyBedrockModelInvoke",
      "Effect": "Deny",
      "Action": [
        "bedrock:InvokeModel",
        "bedrock:InvokeModelWithResponseStream",
        "bedrock:Converse",
        "bedrock:ConverseStream"
      ],
      "Resource": "*"
    }
  ]
}
JSON

  log "Attaching inline deny policy '${DENY_POLICY_NAME}' to role '${TASK_ROLE_NAME}'."
  aws_cmd iam put-role-policy \
    --role-name "${TASK_ROLE_NAME}" \
    --policy-name "${DENY_POLICY_NAME}" \
    --policy-document "file://${policy_file}" >/dev/null

  rm -f "${policy_file}"
}

delete_agreement_if_needed() {
  if [[ "${SKIP_DELETE_AGREEMENT}" -eq 1 ]]; then
    log "Skipping delete-foundation-model-agreement as requested."
    return
  fi

  log "Deleting foundation model agreement for model '${MODEL_ID}'."
  local out
  if ! out="$(aws_cmd bedrock delete-foundation-model-agreement --model-id "${MODEL_ID}" 2>&1)"; then
    if grep -qiE 'ResourceNotFoundException|ValidationException|not found' <<<"${out}"; then
      log "Agreement already absent or not yet created. Continuing."
    else
      echo "${out}" >&2
      fail "delete-foundation-model-agreement failed."
    fi
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model-id) MODEL_ID="${2:-}"; shift 2 ;;
    --region) REGION="${2:-}"; shift 2 ;;
    --profile) PROFILE="${2:-}"; shift 2 ;;
    --task-role-name) TASK_ROLE_NAME="${2:-}"; shift 2 ;;
    --deny-policy-name) DENY_POLICY_NAME="${2:-}"; shift 2 ;;
    --skip-delete-agreement) SKIP_DELETE_AGREEMENT=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) fail "Unknown argument: $1" ;;
  esac
done

[[ -n "${MODEL_ID}" ]] || { usage; fail "--model-id is required."; }

require_bedrock_cli
attach_deny_policy_if_configured
delete_agreement_if_needed

log "Current model availability state:"
aws_cmd bedrock get-foundation-model-availability --model-id "${MODEL_ID}" || true

log "Done. For cost safety, keep deny policy attached to runtime role when demo is idle."

