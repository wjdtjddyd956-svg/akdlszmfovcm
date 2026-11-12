#!/usr/bin/env python3
"""Production uses the real API; menu integration tests live in the 사용안내/생활타이쿤 suite."""
from pathlib import Path
import subprocess,sys
root=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file())
subprocess.run([sys.executable,str(root/'개발자료/공통도구/build_tycoon.py')],check=True)
