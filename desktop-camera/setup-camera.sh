#!/bin/sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(dirname "$SCRIPT_DIR")
PYTHON=${PYTHON:-python3}
"$PYTHON" -c 'import sys; assert (3, 10) <= sys.version_info[:2] <= (3, 12), "Use Python 3.10, 3.11 or 3.12"'
"$PYTHON" -m venv "$PROJECT_DIR/.camera-venv"
"$PROJECT_DIR/.camera-venv/bin/python" -m pip install -r "$SCRIPT_DIR/requirements.txt"
printf '%s\n' 'Camera environment is ready. Run the desktop application from the project root.'
