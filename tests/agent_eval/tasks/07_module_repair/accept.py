#!/usr/bin/env python3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from eval_lib import check_project, expect_project_stdout, forbid, resolve_project, source_text

project = Path(sys.argv[1])
resolve_project(project)
check_project(project)
forbid(source_text(project), "import java", "the library stays in Sprig")
expect_project_stdout(project, "16")
