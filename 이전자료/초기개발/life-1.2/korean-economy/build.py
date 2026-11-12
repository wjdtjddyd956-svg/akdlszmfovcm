#!/usr/bin/env python3
"""Build the current economy release using the actual Paper and Vault APIs."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--paper-server", default=os.environ.get("PAPER_SERVER"),
                    help="Paper 26.3 build 238 server with its libraries and Vault installed")
parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"),
                    help="JDK 25 or newer")
args = parser.parse_args()
if not args.paper_server or not args.java_home:
    parser.error("set PAPER_SERVER and JAVA_HOME, or pass --paper-server and --java-home")
shell = shutil.which("pwsh") or shutil.which("powershell.exe")
if shell is None:
    parser.error("PowerShell is required by the shared build script")
repo = Path(__file__).resolve().parent.parent
subprocess.run([shell, "-NoProfile", "-File", str(repo / "life-server/Build-LifePack.ps1"),
                "-PaperServer", str(Path(args.paper_server).resolve()),
                "-JavaHome", str(Path(args.java_home).resolve())], check=True)
