#!/usr/bin/env python3
"""Current production build: real Paper API only. See 사용안내/생활타이쿤/개발검증.md."""
from pathlib import Path
import subprocess,sys
root=next(p for p in Path(__file__).resolve().parents if (p/'저장소구조.json').is_file())
subprocess.run([sys.executable,str(root/'개발자료/플러그인/생활경제/test_domain.py')],check=True)
subprocess.run([sys.executable,str(root/'개발자료/공통도구/build_tycoon.py')],check=True)
