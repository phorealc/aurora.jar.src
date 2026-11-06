# packetlog — ce que le client envoie réellement

Deux scripts Python, zéro dépendance, aucun mod à compiler.

`mcproxy.py` se place entre le jeu et un serveur local et enregistre chaque
paquet dans un fichier JSONL. Le trafic est relayé octet pour octet : rien
n'est modifié, injecté, réordonné ni supprimé.

`analyze.py` relit ce journal et mesure la cadence des clics, le rythme des
paquets de mouvement, les pics de pose/casse, et met en évidence les blocs que
le serveur a corrigés derrière toi.

## Ce que ça prouve, et ce que ça ne prouve pas

Un cheat ne peut pas tricher sans envoyer de paquets. Ce journal est donc la
vérité terrain sur ce que le client a transmis — pas une interprétation.

En revanche il ne juge pas la **physique** : le reach et le mouvement
demandent de rejouer la simulation du serveur avec la position de chaque
entité. C'est le métier de GrimAC. Les deux sont complémentaires : Grim donne
un verdict, ce journal donne les données.

Il ne voit pas non plus les modules purement visuels (fullbright, chams,
hitboxes), qui n'émettent aucun paquet. Ceux-là relèvent du règlement du
serveur, pas de la détection.

## Prérequis

Python 3.9+, et un serveur de test local avec **`online-mode=false`** dans
`server.properties`. En online mode le flux est chiffré juste après le login et
devient illisible — le proxy le détecte et te le dit au lieu de mentir.

## Mise en place

Lance ton serveur local sur 25565, puis le proxy :

```bash
python3 mcproxy.py --listen 25566 --target 127.0.0.1:25565 --out vanilla.jsonl
```

Dans Minecraft, ajoute le serveur `127.0.0.1:25566` et connecte-toi.

## Protocole d'enregistrement

Fais **exactement la même séquence** dans les deux configurations, sinon la
comparaison ne vaut rien. Change juste le `--out` entre les deux.

1. `idle.jsonl` — immobile, sans rien toucher, 30 s. C'est le témoin : il ne
   contient que les paquets de fond.
2. `clicks.jsonl` — clic gauche dans le vide, 20 fois, à la main, 20 s.
3. `blocks.jsonl` — casse 10 blocs, pose 10 blocs, 30 s.
4. Rejoue les trois **sans le client** (Fabric nu), suffixés `-vanilla`.

## Analyse

Identifie d'abord les ids de ta version :

```bash
python3 analyze.py clicks.jsonl summary
```

```bash
python3 analyze.py idle.jsonl diff clicks.jsonl
```

Les ids qui n'augmentent que dans le second journal sont ceux produits par
l'action. Celui aux alentours de 20/s est le mouvement.

Puis mesure, en remplaçant les ids par les tiens :

```bash
python3 analyze.py clicks.jsonl cps 0x36
```

```bash
python3 analyze.py idle.jsonl timer 0x1a
```

```bash
python3 analyze.py blocks.jsonl rate 0x24
```

```bash
python3 analyze.py blocks.jsonl ghost
```

## Lecture des résultats

| Observation | Conclusion |
|---|---|
| `cps` : écart-type des intervalles < 6 ms | Cadence non humaine → autoclicker |
| `timer` : mouvement > 21/s | Timer / accélération de ticks |
| `timer` : mouvement < 18/s sans lag | Paquets retenus → blink / lag switch |
| `rate` : pic au-dessus du cap accordé par le serveur | Le client dépasse ce qu'il a négocié |
| `ghost` : corrections nombreuses **sans** ligne de caps dans `latest.log` | Prédiction non autorisée |
| `ghost` : corrections **avec** ligne de caps | Comportement négocié, attendu |
| Journal identique au témoin vanilla | Le client n'ajoute aucun paquet |

Pour les caps côté client :

```bash
grep -iE "caps|prediction|refused|marlow|optimizer" ~/.minecraft/logs/latest.log
```

## Vérifier l'outil avant de lui faire confiance

`selftest.py` monte un faux serveur et un faux client qui rejouent un
handshake, la négociation de compression, du mouvement régulier, des clics
parfaitement cadencés et une casse de bloc que le serveur annule. Il vérifie
ensuite que l'analyseur retrouve tout :

```bash
python3 selftest.py
```

10 contrôles doivent passer. Aucun Minecraft n'est nécessaire.

## Format du journal

Une ligne JSON par paquet :

```
{"t":12.3456,"cid":1,"dir":"c2s","phase":"postlogin","id":26,"len":25,"hex":"..."}
```

`t` est en secondes depuis l'ouverture de la connexion, `dir` vaut `c2s` ou
`s2c`, `phase` passe à `postlogin` après le login. Les payloads sont tronqués
à 256 octets côté client et 48 côté serveur.
