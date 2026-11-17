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

# IFT3913 – Tâche 3 : Semgrep dans le CI, vulnérabilités et rickroll

**Auteurs :**

| Nom | Matricule | GitHub |
|---|---|---|
| Aguibou FOFANA | 20332292 | [@AguibouF](https://github.com/AguibouF) |
| Anas HARTI | 20223975 | [@axh23399](https://github.com/axh23399) |

Cas d'étude : [Apache Tika](https://github.com/apache/tika), fork [AguibouF/tika](https://github.com/AguibouF/tika).
La documentation de la tâche 2 est dans [`README.md`](README.md), figée au tag [`Tache2VF`](https://github.com/AguibouF/tika/tree/Tache2VF).

> **Étiquette IAg : Assisté par l'IA** (voir la [section 7](#7-déclaration-dutilisation-de-lia-générative)).

## Sommaire

1. [Modification du workflow GitHub Actions](#1-modification-du-workflow-github-actions)
2. [Validation de la modification](#2-validation-de-la-modification)
3. [Rapport Semgrep avant les modifications](#3-rapport-semgrep-avant-les-modifications)
4. [Les 7 vulnérabilités introduites](#4-les-7-vulnérabilités-introduites)
5. [Comparaison des rapports avant et après](#5-comparaison-des-rapports-avant-et-après)
6. [Rickroll quand un cas de test échoue](#6-rickroll-quand-un-cas-de-test-échoue)
7. [Déclaration d'utilisation de l'IA générative](#7-déclaration-dutilisation-de-lia-générative)
8. [Reproduire les résultats](#8-reproduire-les-résultats)

---

## 1. Modification du workflow GitHub Actions

### 1.1 Quel workflow et pourquoi

L'énoncé demande de modifier « le workflow GitHub Actions du cas d'étude ». On a donc modifié **[`main-jdk17-build.yml`](.github/workflows/main-jdk17-build.yml)**, le workflow de build principal de Tika (compilation, `apache-rat:check`, tests, installation, Javadoc, puis tests de bout en bout), plutôt que de créer un workflow à part.

On y a ajouté un job `semgrep`, et le job `build` existant en dépend :

```yaml
jobs:
  semgrep:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      # Actions figées par SHA de commit (règle Semgrep github-actions-mutable-action-tag).
      - uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803 # v6
        with:
          # Historique complet : le commit de référence doit exister localement.
          fetch-depth: 0
      - name: Install Semgrep
        # Version figée pour que les résultats soient reproductibles.
        run: pipx install semgrep==1.185.0
      - name: Semgrep (new findings only)
        env:
          PR_BASE: ${{ github.event.pull_request.base.sha }}
          PUSH_BEFORE: ${{ github.event.before }}
        run: |
          if [ -n "$PR_BASE" ]; then
            # Pull request : tout ce que la PR introduit par rapport à la branche cible.
            BASE=$(git merge-base "$PR_BASE" HEAD)
          else
            # Push : ce que le push introduit par rapport au commit précédent.
            BASE="$PUSH_BEFORE"
          fi
          # Nouvelle branche (before = 000...) ou push forcé (commit disparu).
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
    # ... job de build d'origine de Tika, inchangé (sauf l'étape rickroll, section 6)
```

### 1.2 Choix de conception

| Choix | Justification |
|---|---|
| **Ne bloquer que les vulnérabilités *nouvelles*** grâce à `--baseline-commit` | Tika contient déjà **84 résultats Semgrep** (section 3). Un job qui échouerait dès qu'il existe un résultat serait rouge à chaque commit, donc inutile. Avec un commit de référence, Semgrep ne signale que les résultats **absents** de ce commit : exactement « une nouvelle vulnérabilité introduite par un commit ». |
| **Commit de référence = commit précédant le push** (`github.event.before`) | Pour un push, c'est précisément ce que le push introduit. |
| **Commit de référence = point de divergence avec la branche cible** pour une pull request | Une PR est jugée sur **tout** ce qu'elle introduit. Si l'on comparait seulement au commit précédent de la PR, une vulnérabilité ajoutée dans un premier commit puis suivie d'un commit anodin passerait au vert et pourrait être fusionnée. |
| **Repli sur `git merge-base origin/main HEAD`** | Sur la création d'une branche, `github.event.before` vaut `000…` ; après un push forcé, l'ancien commit n'existe plus. Le repli évite que le job plante dans ces cas. |
| **`fetch-depth: 0`** | Par défaut, `actions/checkout` ne récupère qu'un seul commit : le commit de référence serait introuvable. |
| **`--error`** | Sans cette option, `semgrep scan` termine avec le code 0 même lorsqu'il trouve des résultats (vérifié localement : 7 résultats, code de sortie 0 ; avec `--error`, code 1). C'est ce qui fait échouer le job. |
| **Job séparé, et `build` avec `needs: semgrep`** | Si Semgrep échoue, le build et les tests de bout en bout ne sont pas lancés et le run est rouge : le processus de build échoue. Le job séparé échoue aussi en quelques minutes, au lieu d'attendre la fin d'un build d'environ une heure. |
| **Règles `p/default` + règles du dépôt (`.semgrep/`)** | `p/default` est l'ensemble de règles publiques recommandé par Semgrep (multi-langage). Le dossier `.semgrep/` contient 7 règles écrites par l'équipe pour les cas que les règles publiques ne couvrent pas (section 4.2). |
| **Tout le dépôt est analysé** | Une vulnérabilité ajoutée dans n'importe quel module doit être bloquée. Le commit de référence garde le scan rapide : seuls les fichiers modifiés sont réanalysés. |
| **Semgrep figé à la version 1.185.0** | Une nouvelle version pourrait changer les résultats d'un jour à l'autre. Limite : les règles du registre (`p/default`) évoluent quand même dans le temps. |
| **Actions figées par SHA de commit** | La règle Semgrep `github-actions-mutable-action-tag` signale les étiquettes modifiables comme `@v6` (risque d'attaque de la chaîne d'approvisionnement). Notre propre job a d'abord été bloqué par cette règle (section 2, run D0). |
| **Rapport JSON conservé** (`upload-artifact`, `if: always()`) | Le rapport reste téléchargeable dans le run, même quand le job échoue. |
| **Aucun compte ni jeton Semgrep** | Tout le monde peut reproduire le scan ; aucun secret à gérer dans le dépôt. |

### 1.3 Modifications annexes nécessaires

- **Exclusions RAT.** Le job `build` lance `apache-rat:check`, qui échouait sur 23 fichiers sans en-tête de licence : les sorties brutes de ChatUniTest archivées pour la tâche 2 (`tika-core/chatunitest-tests/`, `tika-core/ift3913/`) et `rapport_semgrep.txt`. On les a exclus dans [`tika-core/pom.xml`](tika-core/pom.xml) et dans le [`pom.xml`](pom.xml) racine. Sans cela, un commit sans vulnérabilité aurait été rouge pour une autre raison, et la validation n'aurait rien prouvé.
- **Première version remplacée.** Une première version (`semgrep.yml`, branche `tache3`) lançait `semgrep scan --config .semgrep --text tika-core/src/main/java`. Elle ne pouvait jamais échouer (pas d'`--error`), ne distinguait pas les vulnérabilités nouvelles, n'analysait qu'un dossier et n'était reliée à aucun build. Elle a été remplacée par le job décrit ci-dessus.
- **Workflows désactivés.** Comme pour la tâche 2, seuls `main jdk17 build` et `tache2 tests` restent actifs sur le fork ; les autres workflows de Tika (builds Windows, JDK 21/25, Docker) ont été désactivés dans l'onglet Actions.
- **Module d'intégration S3 exclu du build.** Les premiers builds complets ([run 44022015592](https://github.com/AguibouF/tika/actions/runs/44022015592), [run 44023203879](https://github.com/AguibouF/tika/actions/runs/44023203879)) ont échoué après 34 minutes sur `S3PipeIntegrationTest` : ce test de Tika télécharge l'image Docker `quay.io/minio/minio:latest`, qui n'est plus publique (`401 UNAUTHORIZED`). L'échec est sans rapport avec le code du dépôt. Le module est exclu avec `-pl '!:tika-pipes-s3-integration-tests'` dans la commande Maven du job `build`. Sur ces deux runs, le rickroll s'est déclenché : c'est le comportement voulu, puisqu'un cas de test a échoué (section 6).

## 2. Validation de la modification

Chaque scénario correspond à un commit poussé sur `main` (ou à une pull request) et à un run de `main jdk17 build`.

| # | Commit | Scénario | Attendu | Résultat |
|---|---|---|---|---|
| D0 | `39d0eea` | Premier ajout du job, avec des actions non figées (`@v6`, `@v4`) | ❌ | ❌ [run 44017138427](https://github.com/AguibouF/tika/actions/runs/44017138427) : `semgrep` en échec, `build` et `e2e-tests` ignorés |
| V1 | `b18a717` | Correction : actions figées par SHA, aucune nouvelle vulnérabilité | ✅ | ✅ [run 44017844653](https://github.com/AguibouF/tika/actions/runs/44017844653) : `semgrep` réussi (build annulé manuellement) |
| V1 bis | `aacaf61` | Règles TLS et jeton généralisées, aucun code vulnérable | ✅ | [run 44022015592](https://github.com/AguibouF/tika/actions/runs/44022015592) : `semgrep` ✅ ; `build` ❌ sur `S3PipeIntegrationTest`, sans rapport avec Semgrep (section 1.3) |
| V4 | [PR #12](https://github.com/AguibouF/tika/pull/12) `tache3` → `main` | Pull request qui introduit les 7 vulnérabilités | ❌ | ❌ [run 44027966987](https://github.com/AguibouF/tika/actions/runs/44027966987) : `semgrep` en échec avec 12 résultats (11 dans les 7 classes, 1 dans l'ancien `semgrep.yml`), build ignoré ; PR fermée sans fusion |
| V2 | 7 commits | Chaque commit introduit une vulnérabilité | ❌ ×7, une seule nouveauté par run | ❌ ×7, chaque run ne signale que le fichier de son commit (tableau ci-dessous) |
| V3 | `38228fe` | Commit sans vulnérabilité (exclusion du module S3) alors que les 7 vulnérabilités sont déjà sur `main` | ✅ | ✅ [run 44030898014](https://github.com/AguibouF/tika/actions/runs/44030898014) : `semgrep`, `build` et `e2e-tests` réussis |

Détail des 7 runs V2 (job `semgrep` en échec, `build` et `e2e-tests` ignorés) :

| N | Commit | Fichier ajouté | Run | Résultats signalés |
|---|---|---|---|---|
| 1 | `e7a787a` | `InsecureSqlQuery` | [44028199777](https://github.com/AguibouF/tika/actions/runs/44028199777) | 3, tous dans ce fichier |
| 2 | `000b3b0` | `InsecureDeserializer` | [44028624086](https://github.com/AguibouF/tika/actions/runs/44028624086) | 2, tous dans ce fichier |
| 3 | `38cb8ba` | `InsecureTlsVerifier` | [44028822958](https://github.com/AguibouF/tika/actions/runs/44028822958) | 3, tous dans ce fichier |
| 4 | `7d33c9b` | `InsecureTokenGenerator` | [44029079186](https://github.com/AguibouF/tika/actions/runs/44029079186) | 2, tous dans ce fichier |
| 5 | `bd7a19c` | `InsecureCredentials` | [44029247814](https://github.com/AguibouF/tika/actions/runs/44029247814) | 1 |
| 6 | `ff8ae97` | `InsecurePasswordVerifier` | [44029422354](https://github.com/AguibouF/tika/actions/runs/44029422354) | 2, tous dans ce fichier |
| 7 | `db463ed` | `InsecureFileReader` | [44029594483](https://github.com/AguibouF/tika/actions/runs/44029594483) | 1 |

**Ce que montre le run D0.** C'est une détection réelle, non prévue : le premier ajout du job utilisait `actions/checkout@v6` et `actions/upload-artifact@v4`. Le log du job affiche `Commit de référence : 8a67565…` puis **2 résultats seulement**, tous deux de la règle `github-actions-mutable-action-tag`, sur les deux lignes ajoutées, et se termine par `exit code 1`. Sur les 84 résultats déjà présents dans Tika, aucun n'a été signalé : le commit de référence fonctionne. Le build n'a pas été lancé.

**Ce que montrent V1 et V3.** Un commit sans nouvelle vulnérabilité passe, même si le dépôt contient déjà des résultats Semgrep (84 avant nos ajouts, 98 après). V3 est le cas le plus parlant : les 7 vulnérabilités sont déjà sur `main`, et pourtant le commit suivant passe entièrement, du scan Semgrep jusqu'aux tests de bout en bout. Une vulnérabilité déjà présente ne bloque donc pas chaque commit suivant.

**Ce que montre V4.** Pour une pull request, le commit de référence est le point de divergence avec `main` : la PR est jugée sur l'ensemble de ce qu'elle introduit. Elle signale 12 résultats :
- **11 dans les 7 classes** et non 14 : la branche `tache3` contient les premières versions de `InsecureTlsVerifier` (lambda) et de `InsecureTokenGenerator` (`Random` dans une variable), que les règles publiques ne reconnaissent pas ; seules les règles de l'équipe, généralisées, les détectent ;
- **1 dans `.github/workflows/semgrep.yml`**, l'ancien workflow présent sur `tache3`, qui utilise `actions/checkout@v6` sans SHA figé (règle `github-actions-mutable-action-tag`). C'est une nouvelle détection réelle : ce fichier n'existe pas sur `main`.

**Ce que montre V2.** Chaque commit de vulnérabilité est poussé seul : le run ne signale que la vulnérabilité de ce commit, car les précédentes font partie du commit de référence.

**Cas non validés par un run.** Le workflow ne se déclenche que sur `main` et sur les PR vers `main` (déclencheurs d'origine de Tika). Le repli pour une nouvelle branche ou un push forcé n'a donc pas été exercé en CI ; il est justifié en section 1.2.

## 3. Rapport Semgrep avant les modifications

- Commit analysé : **`b18a717`** (workflow modifié, aucun code vulnérable).
- Commande : `semgrep scan --metrics=off --config p/default --config .semgrep --json-output=semgrep-avant.json` (Semgrep 1.185.0, fichiers suivis par Git).
- Résultat : **84 résultats**, dont **aucun** venant des 7 règles de l'équipe.

| Règle | Nombre | Exemples de fichiers |
|---|---|---|
| `github-actions-mutable-action-tag` | 22 | workflows de Tika (`@v6`, `@v5`) |
| `unsafe-reflection` | 17 | `ServiceLoader`, `XMLReaderUtils`, `ConfigLoader` |
| `command-injection-process-builder` | 16 | `ExternalParser`, `TesseractOCRParser`, `FileCommandDetector` |
| `unencrypted-socket` | 4 | `NetworkParser`, `PipesServer` |
| `jdbc-sqli` | 3 | `JDBCTableReader`, `H2Util` |
| `no-new-privileges`, `writable-filesystem-service` | 3 + 3 | fichiers `docker-compose` |
| `formatted-sql-string` | 2 | `H2Util`, `JDBCUtil` |
| `disallow-old-tls-versions1` | 2 | `HttpClientFactory` |
| Autres règles (1 résultat chacune) | 12 | XXE (`MimeTypesReader`, `XMLReaderUtils`), `use-of-aes-ecb`, `use-of-sha1`, etc. |

La plupart sont des usages légitimes dans le contexte de Tika (par exemple `ProcessBuilder` pour lancer Tesseract), ce qui justifie de ne pas les bloquer et de ne bloquer que les ajouts.

## 4. Les 7 vulnérabilités introduites

### 4.1 Choix des fichiers

Chaque vulnérabilité est dans une **nouvelle classe** de `tika-core/src/main/java`, rangée dans le package dont le thème lui correspond :

- `org.apache.tika.config` pour ce qui touche à la configuration et à la connexion à des services (identifiants, base de données, TLS, jetons, désérialisation d'objets de configuration) ;
- `org.apache.tika.digest` pour le hachage (c'est le package de Tika qui calcule des empreintes) ;
- `org.apache.tika.io` pour la lecture de fichiers.

On a choisi des classes séparées plutôt que de modifier des classes existantes pour **isoler** chaque vulnérabilité : le comportement de Tika et sa suite de tests restent inchangés, chaque vulnérabilité tient dans un commit d'un seul fichier, et la ligne détectée par Semgrep est sans ambiguïté. La contrepartie est que ces classes ne sont appelées nulle part ; Semgrep étant une analyse statique, cela n'empêche pas la détection. Chaque classe porte un commentaire `IFT3913` indiquant qu'elle est volontairement vulnérable, compile et respecte Checkstyle, forbiddenapis et RAT.

### 4.2 Règles publiques et règles de l'équipe

Toutes les vulnérabilités sont détectées par la configuration du job (`p/default` + `.semgrep/`), soit **14 résultats** au total. **5 types sur 7** sont détectés par des règles publiques de Semgrep. Pour le mot de passe en dur et la traversée de chemin, aucun des six ensembles publics testés (`p/java`, `p/secrets`, `p/security-audit`, `p/owasp-top-ten`, `p/default`, `p/cwe-top-25`) ne signale le code : les règles publiques de traversée de chemin suivent des données venant d'une requête HTTP, et `p/secrets` cherche des formats de secrets connus (clés d'API, jetons), pas un mot de passe arbitraire. D'où les règles de l'équipe dans `.semgrep/`.

Les règles TLS et jeton ont été généralisées pour ne pas être écrites « sur mesure » : vérifiées sur des cas positifs (lambda, `Random` dans une variable, méthode `resetPasswordCode`) et négatifs (vérificateur qui contrôle vraiment le nom d'hôte, `new Random()` dans une méthode `pickColor`).

### 4.3 Fiches

Chaque vulnérabilité a été ajoutée dans un commit seul et poussée seule. Chaque run est ❌ et ne signale, dans le log du job `semgrep`, que les résultats du fichier ajouté.

#### V1. Injection SQL (CWE-89)

| | |
|---|---|
| **Fichier** | [`tika-core/src/main/java/org/apache/tika/config/InsecureSqlQuery.java`](tika-core/src/main/java/org/apache/tika/config/InsecureSqlQuery.java) |
| **Code** | `findUser(connection, username)` construit `"SELECT * FROM users WHERE username = '" + username + "'"` puis l'exécute avec `Statement.executeQuery`. |
| **Pourquoi c'est vulnérable** | La valeur fournie est insérée telle quelle dans la requête. Avec `username = "x' OR '1'='1"`, la condition devient toujours vraie et la requête renvoie tous les utilisateurs ; d'autres valeurs permettent de modifier ou supprimer des données. Il faut une requête paramétrée (`PreparedStatement`). |
| **Semgrep** | 3 résultats : `ift3913-sql-injection` (ligne 33), `formatted-sql-string` (ERROR, CWE-89, ligne 37) et `jdbc-sqli` (WARNING, CWE-89, ligne 37). |
| **Run** | ❌ [run 44028199777](https://github.com/AguibouF/tika/actions/runs/44028199777) (commit `e7a787a`) : seuls les résultats de ce fichier sont signalés, le build est ignoré |

#### V2. Désérialisation de données non fiables (CWE-502)

| | |
|---|---|
| **Fichier** | [`tika-core/src/main/java/org/apache/tika/config/InsecureDeserializer.java`](tika-core/src/main/java/org/apache/tika/config/InsecureDeserializer.java) |
| **Code** | `deserialize(untrustedData)` appelle `new ObjectInputStream(...).readObject()` sur des octets reçus de l'extérieur. |
| **Pourquoi c'est vulnérable** | `readObject` instancie n'importe quelle classe sérialisable présente dans le classpath. Un attaquant peut fournir une chaîne d'objets (« gadget chain ») qui exécute du code pendant la désérialisation. Il faut éviter la sérialisation Java pour des données externes, ou filtrer les classes acceptées (`ObjectInputFilter`). |
| **Semgrep** | 2 résultats : `object-deserialization` (WARNING, CWE-502, ligne 32) et `ift3913-insecure-deserialization` (ligne 35). |
| **Run** | ❌ [run 44028624086](https://github.com/AguibouF/tika/actions/runs/44028624086) (commit `000b3b0`) : seuls les résultats de ce fichier sont signalés, le build est ignoré |

#### V3. Validation TLS désactivée (CWE-295)

| | |
|---|---|
| **Fichier** | [`tika-core/src/main/java/org/apache/tika/config/InsecureTlsVerifier.java`](tika-core/src/main/java/org/apache/tika/config/InsecureTlsVerifier.java) |
| **Code** | `createVerifier()` renvoie un `HostnameVerifier` anonyme dont `verify(...)` retourne toujours `true`. |
| **Pourquoi c'est vulnérable** | Le nom d'hôte du certificat n'est plus comparé à celui du serveur : un certificat valide pour n'importe quel domaine est accepté, ce qui permet une attaque de l'homme du milieu sur une connexion HTTPS. |
| **Semgrep** | 3 résultats ligne 31 : `insecure-hostname-verifier` (WARNING, CWE-295), `bypass-tls-verification` (WARNING) et `ift3913-insecure-tls`. |
| **Run** | ❌ [run 44028822958](https://github.com/AguibouF/tika/actions/runs/44028822958) (commit `38cb8ba`) : seuls les résultats de ce fichier sont signalés, le build est ignoré |

#### V4. Aléatoire non sûr pour un jeton de session (CWE-330)

| | |
|---|---|
| **Fichier** | [`tika-core/src/main/java/org/apache/tika/config/InsecureTokenGenerator.java`](tika-core/src/main/java/org/apache/tika/config/InsecureTokenGenerator.java) |
| **Code** | `generateSessionToken()` renvoie `Long.toHexString(new Random().nextLong())`. |
| **Pourquoi c'est vulnérable** | `java.util.Random` est un générateur congruentiel linéaire, prévisible : quelques jetons observés suffisent à reconstituer son état et à prédire les suivants, donc à usurper une session. Un secret doit venir de `java.security.SecureRandom`. |
| **Semgrep** | 2 résultats ligne 30 : `weak-random` (WARNING, CWE-330) et `ift3913-insecure-session-token`. |
| **Run** | ❌ [run 44029079186](https://github.com/AguibouF/tika/actions/runs/44029079186) (commit `7d33c9b`) : seuls les résultats de ce fichier sont signalés, le build est ignoré |

#### V5. Mot de passe écrit en dur (CWE-798)

| | |
|---|---|
| **Fichier** | [`tika-core/src/main/java/org/apache/tika/config/InsecureCredentials.java`](tika-core/src/main/java/org/apache/tika/config/InsecureCredentials.java) |
| **Code** | `private static final String DATABASE_PASSWORD = "DemoPassword123!";`, renvoyé par `getDatabasePassword()`. |
| **Pourquoi c'est vulnérable** | Le mot de passe est visible par toute personne qui a accès au code source ou au `.jar` (une chaîne constante se lit directement dans le bytecode). Il ne peut pas être changé sans recompiler et se retrouve dans l'historique Git. Il doit venir d'une variable d'environnement ou d'un gestionnaire de secrets. (La valeur est fictive.) |
| **Semgrep** | 1 résultat : `ift3913-hardcoded-password` (ligne 25). Règle de l'équipe : une constante `private static final String` dont le nom contient `PASSWORD` et dont la valeur est une chaîne non vide. |
| **Run** | ❌ [run 44029247814](https://github.com/AguibouF/tika/actions/runs/44029247814) (commit `bd7a19c`) : seuls les résultats de ce fichier sont signalés, le build est ignoré |

#### V6. MD5 pour vérifier un mot de passe (CWE-328)

| | |
|---|---|
| **Fichier** | [`tika-core/src/main/java/org/apache/tika/digest/InsecurePasswordVerifier.java`](tika-core/src/main/java/org/apache/tika/digest/InsecurePasswordVerifier.java) |
| **Code** | `verifyPassword(password, expectedHash)` compare `MessageDigest.getInstance("MD5")` du mot de passe à l'empreinte attendue. |
| **Pourquoi c'est vulnérable** | MD5 est cassé (collisions) et surtout très rapide et sans sel : un attaquant qui obtient les empreintes retrouve les mots de passe par force brute ou avec des tables précalculées. Il faut un algorithme lent et salé (PBKDF2, bcrypt, Argon2). |
| **Semgrep** | 2 résultats ligne 35 : `use-of-md5` (WARNING, CWE-328) et `ift3913-weak-password-hash`. |
| **Run** | ❌ [run 44029422354](https://github.com/AguibouF/tika/actions/runs/44029422354) (commit `ff8ae97`) : seuls les résultats de ce fichier sont signalés, le build est ignoré |

#### V7. Traversée de chemin (CWE-22)

| | |
|---|---|
| **Fichier** | [`tika-core/src/main/java/org/apache/tika/io/InsecureFileReader.java`](tika-core/src/main/java/org/apache/tika/io/InsecureFileReader.java) |
| **Code** | `readFile(userInput)` lit `baseDirectory.resolve(userInput)` avec `Files.readString`, sans vérification. |
| **Pourquoi c'est vulnérable** | Avec `userInput = "../../etc/passwd"` ou un chemin absolu, le fichier lu sort du répertoire autorisé. Il faut normaliser le chemin et vérifier qu'il commence bien par le répertoire de base (`normalize().startsWith(base)`). |
| **Semgrep** | 1 résultat : `ift3913-path-traversal` (ligne 36). Règle de l'équipe : un `resolve` suivi d'une lecture, hors d'un `if` qui vérifie `normalize().startsWith(...)`. |
| **Run** | ❌ [run 44029594483](https://github.com/AguibouF/tika/actions/runs/44029594483) (commit `db463ed`) : seuls les résultats de ce fichier sont signalés, le build est ignoré |

## 5. Comparaison des rapports avant et après

| | Avant (`b18a717`) | Après (`38228fe`) |
|---|---|---|
| Fichiers analysés | 2190 | 2201 |
| Résultats | 84 | **98** (84 + 14) |
| dont règles de l'équipe | 0 | **7** (une par vulnérabilité) |
| Fichiers | `semgrep-avant.json` | `semgrep-apres.json` |

Les 84 résultats d'avant sont tous encore présents après : aucune modification n'en a fait disparaître ni n'en a ajouté hors des 7 classes.

Scan du code final avec le commit « avant » comme référence, pour isoler les nouveautés :

```bash
semgrep scan --metrics=off --config p/default --config .semgrep --baseline-commit b18a7174c657f9a1971c4129a92cb7545df9c901
```

Résultat obtenu : Semgrep liste les 12 commits depuis `b18a717` (règles, rickroll, 7 vulnérabilités, exclusion S3), réanalyse les 12 fichiers modifiés et signale **14 nouveaux résultats, tous dans les 7 classes ajoutées**, aucun ailleurs (ni dans le workflow, ni dans l'action rickroll) :

| Fichier | Nouveaux résultats |
|---|---|
| `InsecureSqlQuery` | `ift3913-sql-injection`, `formatted-sql-string`, `jdbc-sqli` |
| `InsecureDeserializer` | `object-deserialization`, `ift3913-insecure-deserialization` |
| `InsecureTlsVerifier` | `insecure-hostname-verifier`, `bypass-tls-verification`, `ift3913-insecure-tls` |
| `InsecureTokenGenerator` | `weak-random`, `ift3913-insecure-session-token` |
| `InsecureCredentials` | `ift3913-hardcoded-password` |
| `InsecurePasswordVerifier` | `use-of-md5`, `ift3913-weak-password-hash` |
| `InsecureFileReader` | `ift3913-path-traversal` |

Extrait de la sortie :

```
Current version has 18 findings.
Creating git worktree from 'b18a7174c657f9a1971c4129a92cb7545df9c901' to scan baseline.
...
• Findings: 14 (14 blocking)
• Scan was limited to files changed since baseline commit.
Ran 448 rules on 12 files: 14 findings.
```

Les « 18 findings » de la version courante incluent 4 résultats déjà présents au commit de référence (les lignes `actions/checkout@v6` et `actions/setup-java@v5` d'origine du workflow, qui a été modifié) ; Semgrep les retire car ils existent dans `b18a717`.

## 6. Rickroll quand un cas de test échoue

### 6.1 Implémentation

On a écrit une **action composite réutilisable**, [`.github/actions/rickroll`](.github/actions/rickroll) :

- [`action.yml`](.github/actions/rickroll/action.yml) déclare deux paramètres : `reports` (motif des rapports JUnit XML, par défaut ceux de Surefire et Failsafe) et `video` (le lien) ;
- [`rickroll.py`](.github/actions/rickroll/rickroll.py) lit les rapports JUnit produits par Maven. S'il trouve au moins un cas de test en échec ou en erreur, il écrit dans le résumé du run (`$GITHUB_STEP_SUMMARY`) une vignette cliquable vers *Never Gonna Give You Up* et la liste des tests en échec, et ajoute une annotation d'erreur au run. Sinon, il n'affiche rien.

Elle est appelée dans le workflow du cas d'étude, après l'étape Maven du job `build` (tests unitaires) et après celle du job `e2e-tests` :

```yaml
      - name: Build with Maven
        id: build
        run: mvn clean apache-rat:check test install javadoc:aggregate -Pci -B ...
      - name: Rickroll on test failure
        if: failure() && steps.build.outcome == 'failure'
        uses: ./.github/actions/rickroll
```

### 6.2 Choix

| Choix | Justification |
|---|---|
| Action maison plutôt que `random-rickroll` | `random-rickroll` est une action JavaScript pour Node 12, que GitHub n'exécute plus, et elle réagit à l'ouverture d'une issue, pas à l'échec d'un test. |
| Double condition | Le `if:` ne lance l'action que si l'étape Maven a échoué ; l'action vérifie ensuite dans les rapports JUnit qu'un **cas de test** a échoué. Un échec de compilation, de RAT ou de Checkstyle ne déclenche donc pas le rickroll, conformément à l'énoncé. |
| Action composite (Bash + Python) | Ni Node ni Docker, aucune dépendance à installer ; réutilisable par tout workflow avec `uses: ./.github/actions/rickroll`. |
| Rapports lus par expressions régulières | Le module `xml` de Python est signalé par les règles Semgrep publiques (`use-defused-xml`) : l'ajout du rickroll aurait fait échouer le job Semgrep. Les rapports sont des fichiers locaux produits par Maven. |

### 6.3 Validation

Testée localement sur des rapports avec et sans échec, puis sur un vrai rapport Maven produit par un test volontairement en échec (`RickrollDemoTest`, `assertEquals(1, 2)`).

| # | Commit | Scénario | Attendu | Résultat |
|---|---|---|---|---|
| R1 | `e23e550` | Ajout de l'action | ✅ sans rickroll | ❌ [run 44023203879](https://github.com/AguibouF/tika/actions/runs/44023203879) : le build a échoué sur `S3PipeIntegrationTest` (image Docker inaccessible, section 1.3) et **le rickroll s'est déclenché**, à juste titre puisqu'un cas de test a échoué |
| R2 | `e3d7529` | Ajout de `RickrollDemoTest`, qui échoue volontairement | ❌ avec rickroll | ❌ [run 44025017417](https://github.com/AguibouF/tika/actions/runs/44025017417) : `Tests run: 774, Failures: 1` dans `tika-core`, puis l'étape « Rickroll on test failure » affiche le rickroll avec `org.apache.tika.RickrollDemoTest.testNeverGonnaGiveYouUp` |
| R3 | `9361ba6` | Retrait de `RickrollDemoTest` | ✅ sans rickroll | ❌ [run 44026142450](https://github.com/AguibouF/tika/actions/runs/44026142450) : de nouveau `S3PipeIntegrationTest`, rickroll déclenché pour ce test |
| R4 | `38228fe` | Module S3 exclu, tous les tests passent | ✅ sans rickroll | ✅ [run 44030898014](https://github.com/AguibouF/tika/actions/runs/44030898014) : `build` et `e2e-tests` réussis, l'étape rickroll est ignorée |

Les runs R1 et R3 n'étaient pas prévus comme validation, mais ils confirment le comportement : un vrai test de Tika qui échoue déclenche lui aussi le rickroll, avec son nom. R4 montre qu'un run entièrement vert ne rickrolle pas.

## 7. Déclaration d'utilisation de l'IA générative

Un assistant IA conversationnel (Claude, d'Anthropic) a été utilisé pour concevoir et écrire le job Semgrep, les exclusions RAT, la généralisation des règles TLS et jeton, l'action rickroll, et pour structurer ce document. Les résultats chiffrés et les liens proviennent d'exécutions réelles de Semgrep, de Maven et de GitHub Actions.

## 8. Reproduire les résultats

Depuis la racine du dépôt, avec Semgrep 1.185.0 (`pipx install semgrep==1.185.0`) :

```bash
# rapport complet du code courant
semgrep scan --metrics=off --config p/default --config .semgrep --json-output=semgrep-apres.json

# seulement ce qui est nouveau depuis le commit « avant »
semgrep scan --metrics=off --config p/default --config .semgrep --baseline-commit b18a7174c657f9a1971c4129a92cb7545df9c901

# rapport « avant » : se placer sur b18a717 puis relancer la première commande
```
