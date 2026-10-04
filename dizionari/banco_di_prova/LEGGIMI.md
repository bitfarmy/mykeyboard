# Banco di prova del correttore

Misura il correttore su errori di battitura generati in automatico (tasto vicino, lettere
invertite, lettera in più, doppia dimenticata, lettera mancante) e su parole giuste ma rare
che non stanno nel dizionario.

    # S = cartella con dati/ (freq_it.txt, hun_it/, venv/) e il repository in mykeyboard/
    python genera_banco.py $S 2026 200000 500000 cartella-banco     # seme, fascia di parole rare, uscita
    python vecchia.py $S cartella-banco                             # il correttore della v0.1 (serve banco/vecchio_extra_it.txt:
                                                                    #   git show 4181564:TastieraPersonale/app/src/main/assets/parole_extra_it.txt)
    BANCO_DI_PROVA=cartella-banco gradle testDebugUnitTest --tests '*BancoDiProvaTest.misura' -i
    BANCO_DI_PROVA=cartella-banco TARATURA=1 gradle testDebugUnitTest --tests '*BancoDiProvaTest.taratura' -i

I parametri del correttore (`Lessico.Parametri`) sono stati tarati con il seme 42 (parole rare
dalla posizione 90.000) e verificati con il seme 2026 (parole rare dalla posizione 200.000).
