// Edge Function: crear-empleado (Plan Fase 1d, Entregable 2)
// Unico codigo del proyecto con privilegios elevados: crear un usuario en Supabase
// Auth exige la llave service_role, que no puede vivir en el APK.

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const ROLES_VALIDOS = ["admin", "mesero", "cocina"];
const ID_ESTADO_ACTIVO = 1;

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type, apikey",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function responder(cuerpo: unknown, status: number): Response {
  return new Response(JSON.stringify(cuerpo), {
    status,
    headers: { ...cors, "Content-Type": "application/json" },
  });
}

function error(mensaje: string, status: number): Response {
  return responder({ error: mensaje }, status);
}

function hs(extra: Record<string, string> = {}) {
  return {
    apikey: SERVICE_KEY,
    Authorization: `Bearer ${SERVICE_KEY}`,
    "Content-Type": "application/json",
    ...extra,
  };
}

// Misma politica que domain/ValidadorContrasenia del cliente. Se repite a proposito:
// la validacion del cliente es UX, la del servidor es la que manda.
function contraseniaEsValida(v: string): boolean {
  return typeof v === "string" && v.length >= 8 &&
    /[A-Z]/.test(v) && /[a-z]/.test(v) && /[0-9]/.test(v) && /[^A-Za-z0-9]/.test(v);
}

async function apodoDisponible(nombres: string, apellidos: string): Promise<string> {
  const limpiar = (t: string) =>
    t.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/[^a-z]/g, "");
  const inicial = limpiar(nombres).charAt(0) || "u";
  const apellido = limpiar(apellidos.split(/\s+/)[0] || "usuario");
  const base = (inicial + apellido).slice(0, 40) || "usuario";
  for (let i = 0; i < 20; i++) {
    const cand = i === 0 ? base : base + (i + 1);
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/usuarios?select=id_usuario&apodo_usuario=eq.${encodeURIComponent(cand)}`,
      { headers: hs() },
    );
    const filas = await res.json();
    if (Array.isArray(filas) && filas.length === 0) return cand;
  }
  return base + Date.now();
}

async function deshacerUsuarioAuth(id: string): Promise<void> {
  try {
    await fetch(`${SUPABASE_URL}/auth/v1/admin/users/${id}`, { method: "DELETE", headers: hs() });
  } catch (_) { /* el error original ya se le devuelve al cliente */ }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return error("Metodo no permitido", 405);

  const autorizacion = req.headers.get("Authorization");
  if (!autorizacion) return error("Falta el token de sesion", 401);

  // 1. Quien llama. No alcanza con que la app no muestre el boton.
  const resUsuario = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { apikey: SERVICE_KEY, Authorization: autorizacion },
  });
  if (!resUsuario.ok) return error("Sesion invalida o vencida", 401);
  const llamante = await resUsuario.json();
  const idLlamante: string | undefined = llamante?.id;
  if (!idLlamante) return error("Sesion invalida", 401);

  const resPerfil = await fetch(
    `${SUPABASE_URL}/rest/v1/perfiles?select=rol,activo&id=eq.${idLlamante}`,
    { headers: hs() },
  );
  const perfiles = await resPerfil.json();
  const perfil = Array.isArray(perfiles) ? perfiles[0] : null;
  if (!perfil || perfil.rol !== "admin" || perfil.activo !== true) {
    return error("Solo un administrador activo puede crear empleados", 403);
  }

  // 2. Validar cuerpo
  let cuerpo: Record<string, string>;
  try {
    cuerpo = await req.json();
  } catch (_) {
    return error("Cuerpo invalido", 400);
  }

  const nombres = (cuerpo.nombres ?? "").trim();
  const apellidos = (cuerpo.apellidos ?? "").trim();
  const identidad = (cuerpo.identidad ?? "").trim();
  const correo = (cuerpo.correo ?? "").trim().toLowerCase();
  const telefono = (cuerpo.telefono ?? "").trim();
  const rol = (cuerpo.rol ?? "").trim().toLowerCase();
  const contrasenia = cuerpo.contrasenia ?? "";

  if (!nombres || !apellidos) return error("Nombres y apellidos son obligatorios", 400);
  if (!identidad) return error("La identidad es obligatoria", 400);
  if (!correo.includes("@")) return error("El correo no es valido", 400);
  if (!ROLES_VALIDOS.includes(rol)) return error("El rol no es valido", 400);
  if (!contraseniaEsValida(contrasenia)) {
    return error("La contrasena debe tener 8 caracteres, mayuscula, minuscula, numero y simbolo", 400);
  }

  const resRol = await fetch(
    `${SUPABASE_URL}/rest/v1/roles?select=id_rol&nombre_rol=eq.${rol}`,
    { headers: hs() },
  );
  const roles = await resRol.json();
  const idRol: number | undefined = Array.isArray(roles) ? roles[0]?.id_rol : undefined;
  if (!idRol) return error("El rol no existe en el catalogo", 400);

  // 3. Cuenta de acceso. email_confirm: true => entra de inmediato.
  const resAuth = await fetch(`${SUPABASE_URL}/auth/v1/admin/users`, {
    method: "POST",
    headers: hs(),
    body: JSON.stringify({ email: correo, password: contrasenia, email_confirm: true }),
  });
  if (!resAuth.ok) {
    const detalle = await resAuth.json().catch(() => ({}));
    const yaExiste = resAuth.status === 422 ||
      String(detalle?.msg ?? detalle?.error_description ?? "").includes("already");
    return error(
      yaExiste ? "Ya existe una cuenta con ese correo" : "No se pudo crear la cuenta de acceso",
      yaExiste ? 409 : 502,
    );
  }
  const idAuthUser: string = (await resAuth.json()).id;

  // 4. Filas de negocio, con deshacer si algo falla
  try {
    const resEmpleado = await fetch(`${SUPABASE_URL}/rest/v1/empleados`, {
      method: "POST",
      headers: hs({ Prefer: "return=representation" }),
      body: JSON.stringify({
        nombres, apellidos, identidad,
        telefono: telefono || null,
        correo, id_estado: ID_ESTADO_ACTIVO,
      }),
    });
    if (!resEmpleado.ok) {
      const detalle = await resEmpleado.text();
      throw new Error(detalle.includes("uq_empleados_identidad")
        ? "Ya existe un empleado con esa identidad"
        : "No se pudo guardar el empleado");
    }
    const idEmpleado: number = (await resEmpleado.json())[0].id_empleado;

    const apodo = await apodoDisponible(nombres, apellidos);
    const resUsuarioFila = await fetch(`${SUPABASE_URL}/rest/v1/usuarios`, {
      method: "POST",
      headers: hs(),
      body: JSON.stringify({
        apodo_usuario: apodo, id_rol: idRol, id_empleado: idEmpleado,
        id_estado: ID_ESTADO_ACTIVO, id_auth_user: idAuthUser,
      }),
    });
    if (!resUsuarioFila.ok) throw new Error("No se pudo crear el usuario del sistema");

    // perfiles es la tabla que consulta el login para decidir el acceso.
    const resPerfilNuevo = await fetch(`${SUPABASE_URL}/rest/v1/perfiles`, {
      method: "POST",
      headers: hs(),
      body: JSON.stringify({
        id: idAuthUser, nombre: nombres + " " + apellidos, rol, activo: true,
      }),
    });
    if (!resPerfilNuevo.ok) throw new Error("No se pudo crear el perfil de acceso");

    return responder({ id_empleado: idEmpleado, id_auth_user: idAuthUser, apodo_usuario: apodo }, 201);
  } catch (e) {
    // Sin esto quedaria una cuenta de Auth huerfana: podria iniciar sesion, no
    // tendria perfil, y el login la rechazaria sin que nadie sepa por que.
    await deshacerUsuarioAuth(idAuthUser);
    return error(e instanceof Error ? e.message : "No se pudo crear el empleado", 400);
  }
});
