#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
env_file="$project_dir/.env.live-model"

if [ ! -f "$env_file" ]; then
  echo "Missing $env_file. Copy .env.live-model.example and add your OpenAI API key." >&2
  exit 1
fi

set -a
. "$env_file"
set +a

if [ -z "${OPENAI_API_KEY:-}" ] || [ "$OPENAI_API_KEY" = "replace-with-your-openai-api-key" ]; then
  echo "Set a valid OPENAI_API_KEY in $env_file before running the live-model test." >&2
  exit 1
fi

live_it_classes=${LIVE_IT_CLASSES:-SareeLiveIT}

exec "$project_dir/mvnw" -pl application -am -Pintegration \
  -Dtest=SareeEventCapabilityTest \
  -Dit.test="$live_it_classes" verify
