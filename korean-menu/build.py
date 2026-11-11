#!/usr/bin/env python3
"""Production uses the real API; menu integration tests live in the tycoon suite."""
from pathlib import Path
import subprocess,sys
root=Path(__file__).resolve().parents[1]
subprocess.run([sys.executable,str(root/'tools/build_tycoon.py')],check=True)
