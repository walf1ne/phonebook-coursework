#!/bin/sh
cd "$(dirname "$0")" || exit 1
exec sh run.command --db=examples/demo.pbk
