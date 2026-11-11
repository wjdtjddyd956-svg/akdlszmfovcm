#!/usr/bin/env python3
from pathlib import Path
import subprocess,sys
root=Path(__file__).resolve().parents[1]
subprocess.run([sys.executable,str(root/'korean-pack-setup/test_config.py')],check=True)
subprocess.run([sys.executable,str(root/'tools/build_tycoon.py')],check=True)
