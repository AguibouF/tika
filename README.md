<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# IFT3913 – Tâche 3 : Semgrep, vulnérabilités et rickroll

| Nom | Matricule | GitHub |
|---|---|---|
| Aguibou FOFANA | 20332292 | [@AguibouF](https://github.com/AguibouF) |
| Anas HARTI | 20223975 | [@axh23399](https://github.com/axh23399) |

Cas d'étude : [Apache Tika](https://github.com/apache/tika) (fork [AguibouF/tika](https://github.com/AguibouF/tika)). La tâche 2 est dans [`README.md`](README.md), figée au tag [`Tache2VF`](https://github.com/AguibouF/tika/tree/Tache2VF).

> **Étiquette IAg : Assisté par l'IA** (voir [section 7](#7-utilisation-de-lia)).

1. [Le workflow](#1-le-workflow)
2. [Validation](#2-validation)
3. [Rapport avant](#3-rapport-avant)
4. [Les 7 vulnérabilités](#4-les-7-vulnérabilités)
5. [Avant / après](#5-avant--après)
6. [Rickroll](#6-rickroll)
7. [Utilisation de l'IA](#7-utilisation-de-lia)
8. [Reproduire](#8-reproduire)

## 1. Le workflow

On a modifié le workflow de build de Tika, [`main-jdk17-build.yml`](.github/workflows/main-jdk17-build.yml), en ajoutant un job `semgrep`. Le job `build` en dépend (`needs: semgrep`) : si Semgrep trouve une nouvelle vulnérabilité, le build et les tests e2e ne sont pas lancés et le run est rouge.

```yaml
  semgrep:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803 # v6
        with:
          fetch-depth: 0
      - name: Install Semgrep
        run: pipx install semgrep==1.185.0
      - name: Semgrep (new findings only)
        env:
          PR_BASE: ${{ github.event.pull_request.base.sha }}
          PUSH_BEFORE: ${{ github.event.before }}
        run: |
          if [ -n "$PR_BASE" ]; then
            BASE=$(git merge-base "$PR_BASE" HEAD)
          else
            BASE="$PUSH_BEFORE"
          fi
          if [ -z "$BASE" ] || ! git cat-file -e "$BASE^{commit}" 2>/dev/null; then
            BASE=$(git merge-base origin/main HEAD)
          fi
          echo "Commit de référence : $BASE"
          semgrep scan --metrics=off \
            --config p/default --config .semgrep \
            --baseline-commit "$BASE" \
            --error --json-output=semgrep.json
      - name: Upload Semgrep report
        if: always()
        uses: actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02 # v4
        with:
          name: semgrep-report
          path: semgrep.json
          if-no-files-found: ignore

  build:
    needs: semgrep
```

### Nos choix

- **Seulement les nouvelles vulnérabilités** (`--baseline-commit`). Tika a déjà 84 résultats Semgrep (section 3). Sans référence, le job serait rouge à chaque commit.
- **La référence dépend de l'événement.** Pour un push, c'est le commit d'avant (`github.event.before`). Pour une PR, c'est le point de divergence avec la branche cible : on juge toute la PR, sinon un commit anodin à la fin la ferait passer. Pour une nouvelle branche (`before` = `000…`) ou un push forcé, on retombe sur `git merge-base origin/main HEAD`.
- **`fetch-depth: 0`**, sinon le commit de référence n'est pas dans le clone.
- **`--error`** : sans ça, `semgrep scan` renvoie 0 même s'il trouve quelque chose (testé : 7 résultats, code 0 ; avec `--error`, code 1).
- **Règles** : `p/default` (règles publiques recommandées par Semgrep) et nos 7 règles dans [`.semgrep/`](.semgrep) pour ce que les règles publiques ne voient pas (section 4).
- **Tout le dépôt est scanné.** Avec la référence, Semgrep ne réanalyse que les fichiers modifiés, donc ça reste rapide (moins d'une minute).
- **Versions figées** : Semgrep 1.185.0, et les actions par SHA plutôt que `@v6`. Limite : les règles de `p/default` peuvent quand même évoluer.
- **Rapport JSON gardé** en artefact, même si le job échoue.
- **Pas de compte Semgrep**, donc pas de secret, et n'importe qui peut refaire le scan.

### Autres changements

- **RAT.** `apache-rat:check` échouait sur 23 fichiers sans licence (archives ChatUniTest de la tâche 2, `rapport_semgrep.txt`). On les a exclus dans [`tika-core/pom.xml`](tika-core/pom.xml) et le [`pom.xml`](pom.xml) racine. Sans ça, un commit propre aurait été rouge pour une autre raison.
- **Module S3 exclu du build.** `S3PipeIntegrationTest` télécharge l'image `quay.io/minio/minio:latest`, qui répond maintenant `401 UNAUTHORIZED`. Le build échouait après 34 min, sans rapport avec notre code ([run 44022015592](https://github.com/AguibouF/tika/actions/runs/44022015592)). On l'exclut avec `-pl '!:tika-pipes-s3-integration-tests'`.
- **Ancienne version.** Notre premier `semgrep.yml` (branche `tache3`) ne pouvait jamais échouer : pas d'`--error`, pas de référence, un seul dossier scanné, pas lié au build. On l'a remplacé.
- **Workflows.** Seuls `main jdk17 build` et `tache2 tests` sont actifs sur le fork. Les autres (Windows, JDK 21/25, Docker) sont désactivés.

## 2. Validation

| # | Commit | Scénario | Résultat |
|---|---|---|---|
| D0 | `39d0eea` | Premier job, actions en `@v6` / `@v4` | ❌ [44017138427](https://github.com/AguibouF/tika/actions/runs/44017138427) |
| V1 | `b18a717` | Actions figées, rien de nouveau | ✅ [44017844653](https://github.com/AguibouF/tika/actions/runs/44017844653) (semgrep OK, build annulé à la main) |
| V2 | 7 commits | Une vulnérabilité par commit | ❌ ×7 (tableau ci-dessous) |
| V3 | `38228fe` | Commit propre, les 7 vulnérabilités déjà sur `main` | ✅ [44030898014](https://github.com/AguibouF/tika/actions/runs/44030898014) (semgrep, build et e2e OK) |
| V4 | [PR #12](https://github.com/AguibouF/tika/pull/12) | PR `tache3` → `main` avec les vulnérabilités | ❌ [44027966987](https://github.com/AguibouF/tika/actions/runs/44027966987), PR fermée sans fusion |

| Vuln. | Commit | Fichier | Run | Résultats |
|---|---|---|---|---|
| 1 | `e7a787a` | `InsecureSqlQuery` | [44028199777](https://github.com/AguibouF/tika/actions/runs/44028199777) | 3 |
| 2 | `000b3b0` | `InsecureDeserializer` | [44028624086](https://github.com/AguibouF/tika/actions/runs/44028624086) | 2 |
| 3 | `38cb8ba` | `InsecureTlsVerifier` | [44028822958](https://github.com/AguibouF/tika/actions/runs/44028822958) | 3 |
| 4 | `7d33c9b` | `InsecureTokenGenerator` | [44029079186](https://github.com/AguibouF/tika/actions/runs/44029079186) | 2 |
| 5 | `bd7a19c` | `InsecureCredentials` | [44029247814](https://github.com/AguibouF/tika/actions/runs/44029247814) | 1 |
| 6 | `ff8ae97` | `InsecurePasswordVerifier` | [44029422354](https://github.com/AguibouF/tika/actions/runs/44029422354) | 2 |
| 7 | `db463ed` | `InsecureFileReader` | [44029594483](https://github.com/AguibouF/tika/actions/runs/44029594483) | 1 |

Ce qu'on en retient :

- **D0** est une vraie détection qu'on n'avait pas prévue. Notre premier job utilisait des actions en `@v6` et `@v4`, et la règle `github-actions-mutable-action-tag` l'a bloqué. Le log ne montre que ces **2** résultats, aucun des 84 déjà présents : la référence marche.
- **V2** : chaque run ne signale que le fichier de son commit, car les vulnérabilités précédentes font déjà partie de la référence.
- **V3** : les 7 vulnérabilités sont dans le code, et pourtant le commit suivant passe jusqu'au bout. Une vulnérabilité déjà là ne bloque pas les commits suivants.
- **V4** : la PR signale 12 résultats. Il y en a 11 dans les 7 classes et non 14, car `tache3` a nos premières versions TLS et jeton, que seules nos règles détectent. Le 12e est dans l'ancien `semgrep.yml` (`actions/checkout@v6`).
- **Pas testé en CI** : le cas nouvelle branche / push forcé, car le workflow ne tourne que sur `main` et les PR vers `main`.

## 3. Rapport avant

Commit `b18a717`, sans code vulnérable, avec `semgrep scan --metrics=off --config p/default --config .semgrep`. Résultat : **84 résultats**, aucun venant de nos règles ([`semgrep-avant.json`](semgrep-avant.json)).

| Règle | Nb | Exemples |
|---|---|---|
| `github-actions-mutable-action-tag` | 22 | workflows de Tika |
| `unsafe-reflection` | 17 | `ServiceLoader`, `ConfigLoader` |
| `command-injection-process-builder` | 16 | `ExternalParser`, `TesseractOCRParser` |
| `unencrypted-socket` | 4 | `NetworkParser`, `PipesServer` |
| `jdbc-sqli`, `formatted-sql-string` | 3 + 2 | `JDBCTableReader`, `H2Util` |
| règles docker-compose | 6 | `docker-compose-*.yml` |
| autres (XXE, AES/ECB, SHA-1, etc.) | 14 | |

La plupart sont des usages normaux pour Tika (lancer Tesseract avec `ProcessBuilder`, par exemple). C'est pour ça qu'on ne bloque que ce qui est nouveau.

## 4. Les 7 vulnérabilités

**Fichiers.** Chaque vulnérabilité est dans une nouvelle classe de `tika-core/src/main/java`, rangée selon son thème : `config` (identifiants, base de données, TLS, jetons, désérialisation), `digest` (hachage), `io` (fichiers). On a préféré des classes à part pour ne pas toucher au comportement de Tika ni à ses tests, et pour avoir un fichier par commit. Ces classes ne sont appelées nulle part, mais Semgrep fait de l'analyse statique, donc ça ne change rien à la détection. Elles compilent et passent Checkstyle, forbiddenapis et RAT.

**Règles.** 5 types sur 7 sont détectés par des règles publiques. Pour le mot de passe en dur et la traversée de chemin, on a essayé 6 ensembles publics (`p/java`, `p/secrets`, `p/security-audit`, `p/owasp-top-ten`, `p/default`, `p/cwe-top-25`) sans résultat, d'où nos propres règles. On a aussi rendu nos règles TLS et jeton plus générales, pour qu'elles ne détectent pas seulement notre code. On les a testées sur une lambda et sur `resetPasswordCode()`, qui sont détectées, et sur un vrai vérificateur et sur `pickColor()`, qui ne le sont pas.

| # | Type (CWE) | Fichier | Pourquoi c'est vulnérable | Règles Semgrep |
|---|---|---|---|---|
| 1 | Injection SQL (89) | [`config/InsecureSqlQuery`](tika-core/src/main/java/org/apache/tika/config/InsecureSqlQuery.java) | `findUser` concatène `username` dans la requête. Avec `x' OR '1'='1`, on récupère tous les utilisateurs. Il faut un `PreparedStatement`. | `formatted-sql-string`, `jdbc-sqli`, `ift3913-sql-injection` |
| 2 | Désérialisation (502) | [`config/InsecureDeserializer`](tika-core/src/main/java/org/apache/tika/config/InsecureDeserializer.java) | `readObject()` sur des octets venant de l'extérieur. Une chaîne de « gadgets » peut exécuter du code. Il faudrait filtrer les classes (`ObjectInputFilter`). | `object-deserialization`, `ift3913-insecure-deserialization` |
| 3 | TLS non vérifié (295) | [`config/InsecureTlsVerifier`](tika-core/src/main/java/org/apache/tika/config/InsecureTlsVerifier.java) | Le `HostnameVerifier` renvoie toujours `true`, donc n'importe quel certificat passe : attaque de l'homme du milieu possible. | `insecure-hostname-verifier`, `bypass-tls-verification`, `ift3913-insecure-tls` |
| 4 | Aléatoire faible (330) | [`config/InsecureTokenGenerator`](tika-core/src/main/java/org/apache/tika/config/InsecureTokenGenerator.java) | Le jeton de session vient de `new Random().nextLong()`. `Random` est prévisible : quelques jetons suffisent pour deviner les suivants. Il faut `SecureRandom`. | `weak-random`, `ift3913-insecure-session-token` |
| 5 | Mot de passe en dur (798) | [`config/InsecureCredentials`](tika-core/src/main/java/org/apache/tika/config/InsecureCredentials.java) | `DATABASE_PASSWORD = "DemoPassword123!"` (fictif) se lit dans le source, le `.jar` et l'historique Git. | `ift3913-hardcoded-password` |
| 6 | MD5 pour mots de passe (328) | [`digest/InsecurePasswordVerifier`](tika-core/src/main/java/org/apache/tika/digest/InsecurePasswordVerifier.java) | MD5 est rapide et sans sel, donc cassable par force brute ou tables précalculées. Il faut PBKDF2, bcrypt ou Argon2. | `use-of-md5`, `ift3913-weak-password-hash` |
| 7 | Traversée de chemin (22) | [`io/InsecureFileReader`](tika-core/src/main/java/org/apache/tika/io/InsecureFileReader.java) | `baseDirectory.resolve(userInput)` sans vérification : `../../etc/passwd` sort du dossier. Il faut vérifier avec `normalize().startsWith(base)`. | `ift3913-path-traversal` |

Les runs de chaque commit sont dans la section 2.

## 5. Avant / après

| | Avant (`b18a717`) | Après (`38228fe`) |
|---|---|---|
| Fichiers analysés | 2190 | 2201 |
| Résultats | 84 | **98** |
| dont nos règles | 0 | 7 |
| Rapport | [`semgrep-avant.json`](semgrep-avant.json) | [`semgrep-apres.json`](semgrep-apres.json) |

Pour isoler ce qui est nouveau, on a relancé le scan avec `b18a717` comme référence :

```bash
semgrep scan --metrics=off --config p/default --config .semgrep --baseline-commit b18a7174c657f9a1971c4129a92cb7545df9c901
```

```
Current version has 18 findings.
...
• Findings: 14 (14 blocking)
Ran 448 rules on 12 files: 14 findings.
```

Il y a **14 nouveaux résultats, tous dans les 7 classes**, et rien dans le workflow ni dans l'action rickroll. Les « 18 findings » de départ comprennent 4 lignes `@v6` / `@v5` d'origine du workflow, qui existaient déjà à la référence. Et 84 + 14 = 98 : aucun ancien résultat n'a disparu.

## 6. Rickroll

On a écrit notre propre action composite, [`.github/actions/rickroll`](.github/actions/rickroll) ([`action.yml`](.github/actions/rickroll/action.yml), [`rickroll.py`](.github/actions/rickroll/rickroll.py)). Elle lit les rapports JUnit de Maven. Si un test a échoué, elle écrit dans le résumé du run une vignette vers *Never Gonna Give You Up* et la liste des tests en échec, plus une annotation. Sinon, elle ne fait rien. Elle est appelée après l'étape Maven des jobs `build` et `e2e-tests` :

```yaml
      - name: Rickroll on test failure
        if: failure() && steps.build.outcome == 'failure'
        uses: ./.github/actions/rickroll
```

- **Pourquoi pas `random-rickroll`** : elle tourne sous Node 12, que GitHub n'exécute plus, et elle réagit à l'ouverture d'une issue, pas à un test qui échoue.
- **Double condition** : le `if` vérifie que Maven a échoué, puis l'action vérifie qu'un cas de test a échoué. Une erreur de compilation ou de RAT ne rickrolle donc pas.
- **Pas de module `xml` en Python** : Semgrep le signale (`use-defused-xml`), ce qui aurait bloqué notre propre commit. On lit les rapports avec des expressions régulières.

| # | Commit | Scénario | Résultat |
|---|---|---|---|
| R1 | `e23e550` | Ajout de l'action | ❌ [44023203879](https://github.com/AguibouF/tika/actions/runs/44023203879) : `S3PipeIntegrationTest` échoue, rickroll déclenché |
| R2 | `e3d7529` | `RickrollDemoTest` avec `assertEquals(1, 2)` | ❌ [44025017417](https://github.com/AguibouF/tika/actions/runs/44025017417) : rickroll avec `RickrollDemoTest.testNeverGonnaGiveYouUp` |
| R3 | `9361ba6` | Retrait du test | ❌ [44026142450](https://github.com/AguibouF/tika/actions/runs/44026142450) : encore S3, rickroll déclenché |
| R4 | `38228fe` | S3 exclu, tout passe | ✅ [44030898014](https://github.com/AguibouF/tika/actions/runs/44030898014) : étape rickroll ignorée |

R1 et R3 n'étaient pas prévus, mais ils montrent qu'un vrai test de Tika qui échoue déclenche aussi le rickroll. R4 montre qu'un run vert ne rickrolle pas. Petit défaut : quand un test plante à l'initialisation (comme S3), son nom s'affiche avec un point à la fin (`S3PipeIntegrationTest.`).

## 7. Utilisation de l'IA

On a utilisé Claude (Anthropic) pour concevoir le job Semgrep, les exclusions RAT, la généralisation des règles TLS et jeton et l'action rickroll, et pour rédiger ce document. Tous les chiffres et les liens viennent de vraies exécutions de Semgrep, de Maven et de GitHub Actions.

## 8. Reproduire

Depuis la racine du dépôt, avec `pipx install semgrep==1.185.0` :

```bash
# rapport complet
semgrep scan --metrics=off --config p/default --config .semgrep --json-output=semgrep-apres.json

# seulement ce qui est nouveau depuis b18a717
semgrep scan --metrics=off --config p/default --config .semgrep --baseline-commit b18a7174c657f9a1971c4129a92cb7545df9c901
```

Pour le rapport avant, on se place sur `b18a717` et on relance la première commande.
