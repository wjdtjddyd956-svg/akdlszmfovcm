#!/usr/bin/env python3
"""Current production build: real Paper API only. See tycoon/개발검증.md."""
from pathlib import Path
import subprocess,sys
root=Path(__file__).resolve().parents[1]
subprocess.run([sys.executable,str(root/'korean-economy/test_domain.py')],check=True)
subprocess.run([sys.executable,str(root/'tools/build_tycoon.py')],check=True)
