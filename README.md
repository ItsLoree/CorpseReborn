# CorpseReborn - Paper 1.21 Edition

Aggiornamento completo del plugin CorpseReborn dalla versione 1.16 alla 1.21.

## Cosa è cambiato

### Approccio tecnico completamente rinnovato
La versione originale usava **NMS (Net Minecraft Server)** con classi specifiche per ogni versione
(NMSCorpses_v1_16_R3, NMSCorpses_v1_8_R1, ecc.) e pacchetti di rete grezzi per simulare
entità giocatore false.

Dalla versione 1.17 in poi, Mojang ha modificato la struttura interna di Minecraft rendendo
questo approccio incompatibile. La nuova versione usa:

- **ArmorStand API** di Paper — le armature sono entità reali, non pacchetti temporanei
- **SkullMeta con OwningPlayer** — per mostrare la skin del giocatore
- **Paper 1.21 API** — completamente stabile, nessun accesso a classi interne

### Vantaggi della nuova versione
- ✅ Funziona su Paper 1.21.x
- ✅ Nessun codice NMS version-specific
- ✅ Cadaveri visibili a tutti i giocatori automaticamente (entità reali)
- ✅ Cadaveri persistono dopo riavvio server (salvati in corpses.yml)
- ✅ Inventario loot apribile con click destro
- ✅ Armor del giocatore visibile sul cadavere
- ✅ Skin del giocatore sulla testa del cadavere
- ✅ Auto-despawn configurabile
- ✅ API pubblica per altri plugin

## Requisiti
- Paper 1.21.x (non Spigot vanilla — Paper è necessario per alcune API)
- Java 21+
- Maven 3.6+

## Compilazione

```bash
mvn clean package
```

Il `.jar` finale si trova in `target/CorpseReborn-3.0.0.jar`.

## Installazione
1. Copia il `.jar` nella cartella `plugins/` del server
2. Avvia il server
3. Configura `plugins/CorpseReborn/config.yml` secondo necessità
4. `/corpsereborn reload` per ricaricare la config senza riavviare

## Comandi
| Comando | Permesso | Descrizione |
|---------|----------|-------------|
| `/spawncorpse [player]` | `corpses.spawn` | Spawna un cadavere |
| `/removecorpse [radius]` | `corpses.remove` | Rimuove cadaveri nel raggio |
| `/corpsereborn` | — | Info plugin |
| `/corpsereborn reload` | `corpses.reload` | Ricarica config |
| `/resendcorpses` | `corpses.resend` | Info cadaveri attivi |
| `/togglecorpse` | `corpses.toggle` | Attiva/disattiva visibilità cadaveri |

## Struttura progetto
```
src/main/java/org/golde/bukkit/corpsereborn/
├── Main.java                  - Classe principale plugin
├── ConfigData.java            - Gestione configurazione
├── cmds/                      - Comandi
│   ├── CoreCommand.java
│   ├── SpawnCorpseCommand.java
│   ├── RemoveCorpseCommand.java
│   ├── ResendCorpsesCommand.java
│   └── ToggleCorpseCommand.java
├── listeners/                 - Event listeners
│   ├── PlayerDeathListener.java
│   ├── PlayerRespawnListener.java
│   ├── PlayerJoinListener.java
│   ├── PlayerQuitListener.java
│   ├── InventoryClickListener.java
│   └── ChunkLoadListener.java
├── nms/
│   ├── CorpseData.java        - Modello dati cadavere
│   └── CorpseManager.java     - Logica spawn/rimozione/salvataggio
└── CorpseAPI/
    ├── CorpseAPI.java         - API pubblica per altri plugin
    └── events/
        ├── CorpseSpawnEvent.java
        ├── CorpseRemoveEvent.java
        └── CorpseClickEvent.java
```
