# -*- coding: utf-8 -*-
"""Bibliografia unificada y citas en el texto, en estilo IEEE.

Los nueve documentos de origen traian TRES listas de referencias distintas
—una en el estado del arte, otra en arquitectura y otra al final— cada una
numerando desde [1]. En un documento unico eso hace que una misma cita
signifique cosas distintas segun donde aparezca.

Aqui se define una sola lista. Las veintiuna primeras entradas conservan el
orden que ya asumian las citas escritas en el texto ([1] y [2] en la seccion de
arquitectura, [7] a [16] en el estado del arte), de modo que ninguna cita
existente cambia de significado. A partir de la 22 se anaden las fuentes de las
tecnologias que el informe usaba sin citar.

Una referencia que no llegue a citarse NO se incluye en la bibliografia: en
IEEE toda entrada de la lista debe estar referida desde el texto.
"""
import re

# --------------------------------------------------------------------------
# Entradas con numero fijo. El orden es el que ya suponen las citas escritas.
# --------------------------------------------------------------------------
FIJAS = [
    (u"martin", u"R. C. Martin, Clean Architecture: A Craftsman's Guide to Software Structure "
                u"and Design. Boston, MA, EE. UU.: Prentice Hall, 2017."),
    (u"guia_arq", u"Google, «Guide to app architecture», Android Developers. [En línea]. "
                  u"Disponible en: https://developer.android.com/topic/architecture"),
    (u"evans", u"E. Evans, Domain-Driven Design: Tackling Complexity in the Heart of Software. "
               u"Boston, MA, EE. UU.: Addison-Wesley, 2003."),
    (u"fowler", u"M. Fowler, Patterns of Enterprise Application Architecture. "
                u"Boston, MA, EE. UU.: Addison-Wesley, 2002."),
    (u"room", u"Google, «Save data in a local database using Room», Android Developers. "
              u"[En línea]. Disponible en: https://developer.android.com/training/data-storage/room"),
    (u"flow", u"JetBrains, «Kotlin coroutines and Flow documentation». [En línea]. "
              u"Disponible en: https://kotlinlang.org/docs/flow.html"),
    (u"monefy", u"Aimlogic, «Monefy — Budget & Expense Manager». [En línea]. "
                u"Disponible en: https://monefy.me"),
    (u"moneymanager", u"Realbyte Inc., «Money Manager Expense & Budget». [En línea]. "
                      u"Disponible en: https://www.realbyteapps.com"),
    (u"bluecoins", u"Rammig Software, «Bluecoins Finance & Budget». [En línea]. "
                   u"Disponible en: https://www.bluecoinsapp.com"),
    (u"mobills", u"Mobills, «Mobills — Control financiero». [En línea]. "
                 u"Disponible en: https://www.mobills.com.co"),
    (u"wallet", u"BudgetBakers, «Wallet by BudgetBakers». [En línea]. "
                u"Disponible en: https://budgetbakers.com"),
    (u"spendee", u"Spendee, «Spendee — Money Tracker». [En línea]. "
                 u"Disponible en: https://www.spendee.com"),
    (u"ynab", u"You Need A Budget LLC, «YNAB — You Need A Budget». [En línea]. "
              u"Disponible en: https://www.ynab.com"),
    (u"actual", u"Actual Budget, «Actual — A local-first personal finance app». [En línea]. "
                u"Disponible en: https://actualbudget.org"),
    (u"firefly", u"J. F. Grigg, «Firefly III — A free and open source personal finance manager». "
                 u"[En línea]. Disponible en: https://www.firefly-iii.org"),
    (u"ivy", u"Ivy Apps, «Ivy Wallet — Open source money manager». [En línea]. "
             u"Disponible en: https://github.com/Ivy-Apps/ivy-wallet"),
    (u"workmanager", u"Google, «Schedule tasks with WorkManager», Android Developers. [En línea]. "
                     u"Disponible en: https://developer.android.com/topic/libraries/architecture/workmanager"),
    (u"compose", u"Google, «Jetpack Compose», Android Developers. [En línea]. "
                 u"Disponible en: https://developer.android.com/jetpack/compose"),
    (u"kmp", u"JetBrains, «Kotlin Multiplatform documentation». [En línea]. "
             u"Disponible en: https://kotlinlang.org/docs/multiplatform.html"),
    (u"datetime", u"JetBrains, «kotlinx-datetime». [En línea]. "
                  u"Disponible en: https://github.com/Kotlin/kotlinx-datetime"),
    (u"retell", u"Retell AI, «Retell AI — Voice agents platform». [En línea]. "
                u"Disponible en: https://www.retellai.com"),
]

# --------------------------------------------------------------------------
# Entradas nuevas. Reciben numero por orden de primera cita; si no se citan,
# no aparecen en la bibliografia.
# --------------------------------------------------------------------------
NUEVAS = [
    (u"mvvm", u"J. Gossman, «Introduction to Model/View/ViewModel pattern for building WPF apps», "
              u"Microsoft Developer Blogs, oct. 2005. [En línea]. Disponible en: "
              u"https://learn.microsoft.com/archive/blogs/johngossman"),
    (u"sqlite", u"SQLite Consortium, «SQLite». [En línea]. Disponible en: https://www.sqlite.org"),
    (u"migracion", u"Google, «Migrate your Room database», Android Developers. [En línea]. Disponible en: "
                   u"https://developer.android.com/training/data-storage/room/migrating-db-versions"),
    (u"gradle", u"Gradle Inc., «Gradle User Manual». [En línea]. Disponible en: "
                u"https://docs.gradle.org/current/userguide/userguide.html"),
    (u"nulos", u"JetBrains, «Null safety», Kotlin documentation. [En línea]. Disponible en: "
               u"https://kotlinlang.org/docs/null-safety.html"),
    (u"selladas", u"JetBrains, «Sealed classes and interfaces», Kotlin documentation. [En línea]. "
                  u"Disponible en: https://kotlinlang.org/docs/sealed-classes.html"),
    (u"valueclass", u"JetBrains, «Inline value classes», Kotlin documentation. [En línea]. "
                    u"Disponible en: https://kotlinlang.org/docs/inline-classes.html"),
    (u"excepciones", u"JetBrains, «Exceptions», Kotlin documentation. [En línea]. Disponible en: "
                     u"https://kotlinlang.org/docs/exceptions.html"),
    (u"reconocedor", u"Google, «SpeechRecognizer», Android Developers API reference. [En línea]. "
                     u"Disponible en: https://developer.android.com/reference/android/speech/SpeechRecognizer"),
    (u"tts", u"Google, «TextToSpeech», Android Developers API reference. [En línea]. Disponible en: "
             u"https://developer.android.com/reference/android/speech/tts/TextToSpeech"),
    (u"permisos", u"Google, «Notification runtime permission», Android Developers. [En línea]. "
                  u"Disponible en: https://developer.android.com/develop/ui/views/notifications/notification-permission"),
    (u"r8", u"Google, «Shrink, obfuscate, and optimize your app», Android Developers. [En línea]. "
            u"Disponible en: https://developer.android.com/build/shrink-code"),
    (u"pruebas", u"Google, «Test apps on Android», Android Developers. [En línea]. Disponible en: "
                 u"https://developer.android.com/training/testing"),
]

# --------------------------------------------------------------------------
# Disparadores: la primera vez que aparece la expresion en el documento se
# inserta la cita justo detras. Se ordenan de mas especifico a mas general
# para que "Kotlin Multiplatform" no lo capture antes "Kotlin".
# --------------------------------------------------------------------------
DISPARADORES = [
    (u"kmp", r"Kotlin Multiplatform"),
    (u"compose", r"Jetpack Compose"),
    (u"workmanager", r"WorkManager"),
    (u"datetime", r"kotlinx-datetime"),
    (u"retell", r"Retell"),
    (u"evans", r"modelo de dominio"),
    # El patron Repositorio esta catalogado en el libro de Fowler.
    (u"fowler", r"interfaces de repositorio"),
    (u"room", r"\bRoom\b"),
    (u"flow", r"StateFlow"),
    (u"mvvm", r"\bMVVM\b"),
    (u"sqlite", r"SQLite"),
    (u"migracion", r"MIGRATION_1_2|migración de esquema"),
    (u"gradle", r"módulos Gradle|Gradle"),
    (u"nulos", r"seguridad (?:ante|contra) (?:valores )?nulos"),
    (u"selladas", r"jerarquías selladas|sealed class|clases selladas"),
    (u"valueclass", r"value class"),
    (u"excepciones", r"manejo de excepciones"),
    (u"reconocedor", r"SpeechRecognizer"),
    (u"tts", r"TextToSpeech"),
    (u"permisos", r"POST_NOTIFICATIONS"),
    (u"r8", r"\bR8\b"),
    (u"pruebas", r"pruebas instrumentadas|Espresso"),
]

TEXTO = dict(FIJAS + NUEVAS)


class Citador(object):
    """Asigna numeros y va insertando las citas en los bloques de texto."""

    def __init__(self):
        self.numeros = {}
        for i, (clave, _) in enumerate(FIJAS, 1):
            self.numeros[clave] = i
        self.siguiente = len(FIJAS) + 1
        self.usadas = set()
        self.compilados = [(c, re.compile(p)) for c, p in DISPARADORES]
        self.insertadas = 0

    def _numero(self, clave):
        if clave not in self.numeros:
            self.numeros[clave] = self.siguiente
            self.siguiente += 1
        return self.numeros[clave]

    def procesar(self, runs):
        """Devuelve la lista de runs con las citas pendientes ya insertadas."""
        salida = []
        for texto, negrita, cursiva, mono in runs:
            resto = texto
            colocada = True
            while colocada:
                colocada = False
                for clave, patron in self.compilados:
                    if clave in self.usadas:
                        continue
                    m = patron.search(resto)
                    if not m:
                        continue
                    # Se parte el run justo detras de la expresion y la cita
                    # entra como run normal, nunca en monoespaciada.
                    salida.append((resto[:m.end()], negrita, cursiva, mono))
                    salida.append((u" [%d]" % self._numero(clave), False, False, False))
                    resto = resto[m.end():]
                    self.usadas.add(clave)
                    self.insertadas += 1
                    colocada = True
                    break
            if resto:
                salida.append((resto, negrita, cursiva, mono))
        return salida

    def marcar_existentes(self, texto):
        """Registra como usadas las citas que ya venian escritas en el texto."""
        for n in re.findall(r"\[(\d{1,2})\]", texto or ""):
            n = int(n)
            for clave, num in self.numeros.items():
                if num == n:
                    self.usadas.add(clave)

    def lista_final(self):
        """Bibliografia ordenada por numero, solo con lo efectivamente citado."""
        pares = [(num, clave) for clave, num in self.numeros.items()
                 if clave in self.usadas]
        pares.sort()
        return [(num, TEXTO[clave]) for num, clave in pares]

    def sin_citar(self):
        return [c for c, _ in FIJAS + NUEVAS if c not in self.usadas]
