# Sun's recipes

Application Android Kotlin/Jetpack Compose pour scanner, classer et retrouver des recettes papier.

## Fonctionnalités implémentées

- Capture photo avec CameraX et analyse IA vision locale.
- Utilisation prioritaire du cadrage document Google lorsqu’il est disponible, avec repli CameraX.
- Analyse Gemini distante obligatoire, configurée depuis l’écran IA.
- Analyse directe de l’image originale, sans étape OCR.
- Détection de plusieurs recettes sur une même feuille lorsque le modèle les distingue.
- Détection de deux ingrédients principaux et classement par famille.
- Validation et correction du nom et des ingrédients avant l’enregistrement.
- Stockage Room local avec image originale et résultat structuré de l’IA.
- Recherche par nom, ingrédient ou famille.
- Prise en charge des recettes françaises et anglaises : le texte et le nom restent dans la langue du scan, tandis que l’index bilingue permet par exemple de retrouver `chicken` avec `poulet` et `carrot` avec `carotte`.
- Parcours alphabétique par défaut, avec familles visibles lorsque des recettes existent.
- Export JSON complet via le sélecteur de fichiers Android. Si Google Drive est installé et connecté, il apparaît comme emplacement de destination ; l’image du scan est incluse en Base64.
- Import JSON via le bouton fichier : les recettes et leurs scans originaux sont ajoutés au carnet sans supprimer les données existantes.

## Étendre le dictionnaire

Les listes sont dans [ingredient_dictionary.json](app/src/main/assets/ingredient_dictionary.json), pas dans le code Kotlin.

L’écran de validation permet de corriger le résultat de l’IA avant sauvegarde. Aucun modèle volumineux n’est embarqué directement dans l’APK.

Pour un compte Google personnel, l’écran `Configurer Gemini` ouvre Google AI Studio. Saisissez ensuite votre clé Gemini ; elle est chiffrée dans Android Keystore et utilisée en priorité pour les scans. La clé n’est pas intégrée à l’APK.
Sans clé Gemini configurée, aucun scan n’est lancé.

- Ajoutez un terme dans la liste de la famille concernée pour améliorer le classement IA.
- Ajoutez une entrée `{ "fr": "...", "en": "..." }` dans `ingredients` pour rendre la recherche bilingue.
- Conservez un JSON valide et utilisez des guillemets doubles.

Exemple :

```json
{ "fr": "fenugrec", "en": "fenugreek" }
```

## Lancer le projet

1. Ouvrir le dossier dans Android Studio récent.
2. Laisser Android Studio installer le SDK Android 35 et synchroniser Gradle.
3. Lancer l’application sur un téléphone Android 8.0 ou plus récent.
4. Accorder la permission caméra au premier scan.

Le terminal de génération utilisé ici ne contient pas Gradle ni le SDK Android, donc la compilation finale doit être effectuée dans Android Studio ou dans un environnement CI Android.

## Structure

- `app/src/main/java/com/sunrecipes/app/data` : entités, DAO, base Room et export.
- `app/src/main/java/com/sunrecipes/app/ocr` : intégration du modèle vision et interprétation des recettes.
- `MainActivity.kt` : catalogue, recherche et navigation.
- `ScannerScreen.kt` : capture CameraX.
