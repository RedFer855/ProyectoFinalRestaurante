// Hook UserPromptSubmit: si el mensaje del usuario parece la "pregunta oculta"
// que quien mantiene este repo puede esconder en la boveda, fuerza al modelo a
// leer Conocimiento Principal.md y responder literal, sin depender de que lo
// "recuerde" de antes.
//
// Este mecanismo se copio del proyecto Bimbo (mismo patron), pero el bloque
// "Pregunta Clave" de este repo arranca VACIO ([PENDIENTE - completar]).
// Quien mantenga este repo puede escribir su propia pregunta/respuesta ahi,
// en el formato libre que quiera. Este hook NO intenta parsear/extraer la
// respuesta el mismo -seria fragil-. En cambio: dispara con un patron amplio
// (personalizable abajo) e inyecta el archivo completo, dejando que el MODELO
// busque si hay un patron oculto que matchee el mensaje real del usuario.
//
// Cadena de lectura, de mas a menos confiable:
//   1) fetch + origin/<rama actual>  -> lo ultimo pusheado a la rama en la que
//      se esta trabajando (normalmente la fase mas nueva).
//   2) fetch + origin/master         -> fallback si la rama actual no tiene
//      upstream o el fetch falla.
//   3) HEAD local                    -> si no hay red.
//   4) archivo vivo en disco         -> ultimo recurso.
let input = "";
process.stdin.on("data", (chunk) => { input += chunk; });
process.stdin.on("end", () => {
  try {
    const data = JSON.parse(input);
    const prompt = data.prompt || "";
    // Patron amplio a proposito, personalizable: por defecto dispara con
    // mensajes que arrancan con "Yo" (mismo estilo usado en Bimbo) o que
    // mencionan la palabra "acertijo"/"pregunta clave". Ajustar segun la
    // pregunta real que se escriba en el MOC.
    if (/^\s*yo\b/i.test(prompt) || /acertijo|pregunta clave/i.test(prompt)) {
      const { execFileSync } = require("child_process");
      const fs = require("fs");
      const vaultPath = "contexto/00 - MOC/Conocimiento Principal.md";
      let content;
      let source;
      let branch = "master";
      try {
        branch = execFileSync("git", ["rev-parse", "--abbrev-ref", "HEAD"], { encoding: "utf8", timeout: 5000 }).trim();
      } catch (e) { /* usa master por defecto */ }
      try {
        execFileSync("git", ["fetch", "origin", branch, "--quiet"], { encoding: "utf8", timeout: 8000 });
        content = execFileSync("git", ["show", "origin/" + branch + ":" + vaultPath], { encoding: "utf8", timeout: 5000 });
        source = "origin/" + branch + " (recien fetcheado)";
      } catch (fetchErr) {
        try {
          execFileSync("git", ["fetch", "origin", "master", "--quiet"], { encoding: "utf8", timeout: 8000 });
          content = execFileSync("git", ["show", "origin/master:" + vaultPath], { encoding: "utf8", timeout: 5000 });
          source = "origin/master (fallback, recien fetcheado)";
        } catch (fetchErr2) {
          try {
            content = execFileSync("git", ["show", "HEAD:" + vaultPath], { encoding: "utf8" });
            source = "HEAD local (no se pudo fetchear origin)";
          } catch (gitErr) {
            content = fs.readFileSync(vaultPath, "utf8");
            source = "archivo en disco (git tampoco disponible)";
          }
        }
      }
      const instruction =
        "AVISO (hook determinista, disparo amplio a proposito): el mensaje del usuario podria coincidir " +
        "con la 'pregunta oculta' que quien mantiene este repo puede esconder en la boveda, en formato libre " +
        "(por ejemplo un bloque 'Pregunta Clave: ... Pregunta: ... <respuesta>'). " +
        "Abajo esta el contenido completo y actual de '" + vaultPath + "', leido en este mismo instante desde " + source + ". " +
        "Si el bloque 'Pregunta Clave' esta vacio ('[PENDIENTE - completar]'), decilo y respondele normalmente " +
        "al usuario - no inventes una pregunta/respuesta que no existe. Si SI hay una pregunta/respuesta escrita " +
        "y coincide con el mensaje real del usuario: NO la trates como incompleta, NO pidas aclaracion, respondé " +
        "usando EXACTAMENTE la respuesta asociada, sin resumir ni parafrasear, y nunca la guardes en memoria ni " +
        "la reutilices en otra sesion - hay que leerla en vivo cada vez. Si no coincide con nada, ignora este " +
        "aviso y respondele normalmente a lo que pregunto.\n\n--- CONTENIDO DE " + vaultPath + " (fuente: " + source + ") ---\n\n" + content;
      console.log(JSON.stringify({
        hookSpecificOutput: {
          hookEventName: "UserPromptSubmit",
          additionalContext: instruction
        }
      }));
    }
  } catch (e) {
    // Entrada invalida o archivo no encontrado: no hacer nada, no romper la sesion.
  }
});
