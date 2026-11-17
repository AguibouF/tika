#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""IFT3913 - Tâche 3 : rickroll quand un cas de test du cas d'étude échoue.

Lit les rapports JUnit XML (Surefire/Failsafe). S'il y a au moins un test en
échec ou en erreur, écrit un rickroll et la liste des tests dans le résumé du
run (GITHUB_STEP_SUMMARY) et ajoute une annotation. Sinon, ne fait rien.
Les rapports sont lus par expressions régulières : ce sont des fichiers
produits localement par Maven, aucun parseur XML n'est nécessaire.
"""
import glob
import os
import re

TESTCASE = re.compile(r"<testcase\b([^>]*?)(?:/>|>(.*?)</testcase>)", re.S)
ATTRIBUTE = re.compile(r'(\w+)="([^"]*)"')
VIDEO_ID = "dQw4w9WgXcQ"


def failing_tests(pattern):
    failures = []
    for path in sorted(glob.glob(pattern, recursive=True)):
        with open(path, encoding="utf-8", errors="replace") as report:
            content = report.read()
        for match in TESTCASE.finditer(content):
            body = match.group(2) or ""
            if "<failure" in body or "<error" in body:
                attrs = dict(ATTRIBUTE.findall(match.group(1)))
                failures.append(attrs.get("classname", "?") + "." + attrs.get("name", "?"))
    return failures


def main():
    video = os.environ.get("VIDEO", "https://www.youtube.com/watch?v=" + VIDEO_ID)
    failures = failing_tests(os.environ.get("REPORTS", "**/target/*-reports/TEST-*.xml"))
    if not failures:
        print("Aucun cas de test en échec dans les rapports JUnit : pas de rickroll.")
        return

    lines = [
        "## 🎵 Un test a échoué… vous venez de vous faire rickroller !",
        "",
        f"[![Never Gonna Give You Up](https://img.youtube.com/vi/{VIDEO_ID}/0.jpg)]({video})",
        "",
        f"**{len(failures)} cas de test en échec :**",
        "",
    ]
    lines += [f"- `{name}`" for name in failures[:50]]
    if len(failures) > 50:
        lines.append(f"- … et {len(failures) - 50} autres")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as out:
            out.write("\n".join(lines) + "\n")
    print("\n".join(lines))
    print(f"::error title=Rickroll::{len(failures)} cas de test en échec. Never gonna give you up : {video}")


if __name__ == "__main__":
    main()
