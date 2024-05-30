#!/bin/bash

_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source $_DIR/public_config.sh

if [ -f $STATE/infra.ready ]; then
	echo "Infrastructure already ready. If you believe this is a bug, please remove the file $STATE/infra.ready and try again."
	exit 0
fi



touch $STATE/infra.ready