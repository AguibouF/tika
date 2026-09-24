## Tests ajoutés manuellement

Fichier : `tika-core/src/test/java/org/apache/tika/sax/xpath/XPathParserMutantsTest.java`

Après l'exécution de PIT avec les tests originaux et les tests ChatUniTest, 4 mutants de `XPathParser` restaient vivants (lignes 48, 69, 87 et 114). Un test a été écrit pour chacun d'eux. Le `setUp` reprend la configuration de `XPathParserTest` : un préfixe `null` associé à l'espace de noms `null`, et le préfixe `"prefix"` associé à `"test namespace"`.

Résultat PIT avec les 4 classes de test : **32 mutants tués sur 32 (100 %)**, couverture de lignes 50/50.

### 1. `testConstructorRegistersPrefix`

| | |
|---|---|
| **Mutant tué** | ligne 48, `VoidMethodCallMutator` : l'appel à `addPrefix(prefix, namespace)` est retiré du constructeur `XPathParser(String, String)` |
| **Intention** | Vérifier que le constructeur à deux arguments enregistre bien le préfixe, comme un appel explicite à `addPrefix`. |
| **Données** | `new XPathParser("prefix", NS)` puis `parse("/prefix:name")`. On utilise une instance **distincte** de celle du `setUp`, pour que le préfixe ne puisse venir que du constructeur. On emploie un nom préfixé, parce que la résolution d'un préfixe est le seul effet observable de `addPrefix`. |
| **Oracle** | Si le préfixe est enregistré, `parse` renvoie un `NamedElementMatcher`. Descendre dans `(NS, "name")` donne l'état final `ElementMatcher`, donc `matchesElement()` est vrai. Descendre dans `(null, "name")` donne `FAIL`, car l'espace de noms ne correspond pas. Avec le mutant, le préfixe est inconnu et `parse` renvoie `FAIL`, dont `descend` renvoie encore `FAIL`. La première assertion échoue alors. |

### 2. `testDescendantNodeCompatibilitySyntax`

| | |
|---|---|
| **Mutant tué** | ligne 69, `RemoveConditionalMutator_EQUAL_ELSE` : la condition `xpath.equals("/descendant:node()")` est remplacée par `false` |
| **Intention** | Vérifier que l'ancienne syntaxe avec un seul deux-points, gardée « for compatibility » selon le commentaire du code, est interprétée comme `/descendant::node()`. |
| **Données** | La chaîne exacte `"/descendant:node()"`. Les tests originaux et ChatUniTest n'utilisent que la forme `::`. Seule cette variante exécute la seconde moitié du `||`. |
| **Oracle** | Même comportement attendu que `/descendant::node()`, déjà testé dans `XPathParserTest.testDescendantNode` : le nœud courant correspond au texte mais pas à l'élément, et tout descendant, à n'importe quelle profondeur, correspond à l'élément. Avec le mutant, l'expression tombe dans la branche `startsWith("/")`. Le nom `descendant:node()` y est lu comme le préfixe `descendant`, qui n'est pas enregistré, donc `parse` renvoie `FAIL` et `matchesText()` est faux. |

### 3. `testAttributeWithUnknownPrefixFails`

| | |
|---|---|
| **Mutant tué** | ligne 87, `NullReturnValsMutator` : `return Matcher.FAIL` est remplacé par `return null` dans la branche des attributs `/@...` |
| **Intention** | Vérifier qu'un attribut dont le préfixe n'est pas enregistré donne un état d'échec, et non `null`. |
| **Données** | `"/@unknown:name"` : une expression d'attribut syntaxiquement valide, dont le préfixe `unknown` n'est pas déclaré dans le `setUp`. C'est la seule façon d'atteindre le `else` de la branche attribut. |
| **Oracle** | La Javadoc de `parse` dit : « Invalid expressions are not flagged as errors, they just result in a failing evaluation state ». Le résultat attendu est donc exactement la sentinelle `Matcher.FAIL`. On la vérifie avec `assertSame`, parce que `FAIL` est une instance unique et que `Matcher` ne définit pas `equals`. Le mutant renvoie `null`, ce qui fait échouer l'assertion. |

### 4. `testExpressionWithoutLeadingSlashFails`

| | |
|---|---|
| **Mutant tué** | ligne 114, `NullReturnValsMutator` : le `return Matcher.FAIL` du `else` final est remplacé par `return null` |
| **Intention** | Vérifier qu'une expression relative, qui ne commence pas par `/`, n'est pas supportée et donne l'état d'échec. |
| **Données** | `"text()"` : proche d'une expression valide (`/text()`), mais sans la barre initiale. Elle n'est donc captée par aucune des branches précédentes et atteint le `else` final. |
| **Oracle** | Le parseur ne gère que des chemins absolus, et d'après la même Javadoc une expression invalide doit donner `Matcher.FAIL`. On le vérifie avec `assertSame(Matcher.FAIL, ...)`. Le mutant renvoie `null`. |
