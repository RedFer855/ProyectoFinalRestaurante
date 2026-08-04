-- ============================================================================
-- Fase 2c — CRUD de Mesas
-- Migración SQL para Supabase (Proyecto Restaurante)
-- Fecha: 2026-08-03
--
-- INSTRUCCIONES:
-- 1. Verificar el DDL real de la tabla `mesa` antes de ejecutar.
--    La bóveda NO documenta las columnas exactas — ajustar si es necesario.
-- 2. Ejecutar en el SQL Editor del dashboard de Supabase, dentro de una transacción.
-- 3. Verificar con la transacción revertida (ver §2.7 del plan).
-- 4. Correr get_advisors(security) → debe dar 0 errores.
-- ============================================================================

-- ──────────────────────────────────────────────────────────────────────────────
-- 1. Catálogo estado_mesa
-- ──────────────────────────────────────────────────────────────────────────────

create table if not exists public.estado_mesa (
    id_estado_mesa int generated always as identity primary key,
    descripcion    varchar(50) not null,
    constraint uq_estado_mesa_descripcion unique (descripcion)
);

-- Insertar los tres estados solo si no existen
insert into public.estado_mesa (descripcion)
select v from (values ('Libre'), ('Ocupada'), ('Reservada')) as t(v)
where not exists (select 1 from public.estado_mesa where descripcion = t.v);

-- ──────────────────────────────────────────────────────────────────────────────
-- 2. Columnas nuevas en mesa
-- ──────────────────────────────────────────────────────────────────────────────

-- id_estado_mesa: estado operativo (Libre/Ocupada/Reservada)
alter table public.mesa
    add column if not exists id_estado_mesa int not null default 1
        references public.estado_mesa(id_estado_mesa);

-- actualizado_en: habilita sync delta de la Fase 2b
alter table public.mesa
    add column if not exists actualizado_en timestamptz not null default now();

-- Si mesa no tiene ya id_estado → estado_general, agregarlo
-- (default 1 = Activo)
alter table public.mesa
    add column if not exists id_estado int not null default 1
        references public.estado_general(id_estado);

-- ──────────────────────────────────────────────────────────────────────────────
-- 3. Triggers
-- ──────────────────────────────────────────────────────────────────────────────

-- 3.1 Actualizar actualizado_en antes de cada UPDATE
create or replace function public.tocar_actualizado_en_mesa()
returns trigger
language plpgsql
as $$
begin
    new.actualizado_en = now();
    return new;
end;
$$;

drop trigger if exists trg_mesa_actualizado_en on public.mesa;
create trigger trg_mesa_actualizado_en
    before update on public.mesa
    for each row
    execute function public.tocar_actualizado_en_mesa();

-- Revocar EXECUTE a roles de app (función de trigger, no RPC)
revoke execute on function public.tocar_actualizado_en_mesa() from anon;
revoke execute on function public.tocar_actualizado_en_mesa() from authenticated;

-- 3.2 Impedir DELETE物理ico de mesas (baja lógica en su lugar)
create or replace function public.mesa_no_borrar()
returns trigger
language plpgsql
as $$
begin
    if current_setting('request.jwt.claims', true)::json->>'role' is not null then
        raise exception 'Las mesas no se borran, se dan de baja. Borrar una rompería el historial de pedidos.';
    end if;
    return old;
end;
$$;

drop trigger if exists trg_mesa_no_borrar on public.mesa;
create trigger trg_mesa_no_borrar
    before delete on public.mesa
    for each row
    execute function public.mesa_no_borrar();

-- Revocar EXECUTE a roles de app
revoke execute on function public.mesa_no_borrar() from anon;
revoke execute on function public.mesa_no_borrar() from authenticated;

-- ──────────────────────────────────────────────────────────────────────────────
-- 4. RPC: cambiar_estado_mesa
-- ──────────────────────────────────────────────────────────────────────────────

create or replace function public.cambiar_estado_mesa(
    p_id_mesa int,
    p_id_estado_mesa int
) returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if rol_actual() not in ('admin', 'mesero') then
        raise exception 'No tenés permiso para cambiar el estado de una mesa.';
    end if;
    if not exists (select 1 from public.estado_mesa where id_estado_mesa = p_id_estado_mesa) then
        raise exception 'Ese estado de mesa no existe.';
    end if;

    update public.mesa
       set id_estado_mesa = p_id_estado_mesa
     where id_mesa = p_id_mesa
       and id_estado = 1;   -- una mesa dada de baja no cambia de estado operativo

    if not found then
        raise exception 'La mesa no existe o está dada de baja.';
    end if;
end;
$$;

-- Revocar a anon (seguridad: sin sesión no se puede cambiar estado)
revoke execute on function public.cambiar_estado_mesa(int, int) from anon;
grant  execute on function public.cambiar_estado_mesa(int, int) to authenticated;

-- ──────────────────────────────────────────────────────────────────────────────
-- 5. Vista: vista_mesas
-- ──────────────────────────────────────────────────────────────────────────────

create or replace view public.vista_mesas
with (security_invoker = on) as
select m.id_mesa,
       m.numero_mesa,
       m.capacidad,
       m.ubicacion,
       m.id_estado_mesa,
       em.descripcion as estado_mesa,
       m.id_estado,
       (m.id_estado = 1) as activo,
       m.actualizado_en
  from public.mesa m
  join public.estado_mesa em on em.id_estado_mesa = m.id_estado_mesa;

-- ──────────────────────────────────────────────────────────────────────────────
-- 6. RLS
-- ──────────────────────────────────────────────────────────────────────────────

-- Habilitar RLS en mesa (si no está habilitado)
ALTER TABLE public.mesa ENABLE ROW LEVEL SECURITY;

-- SELECT: cualquier rol con sesión activa
DROP POLICY IF EXISTS "mesa_select_authenticated" ON public.mesa;
CREATE POLICY "mesa_select_authenticated" ON public.mesa
    FOR SELECT
    TO authenticated
    USING (true);

-- INSERT/UPDATE/DELETE: solo admin
DROP POLICY IF EXISTS "mesa_admin_all" ON public.mesa;
CREATE POLICY "mesa_admin_all" ON public.mesa
    FOR ALL
    TO authenticated
    USING (
        exists (
            select 1 from public.usuarios u
            where u.id = auth.uid()
              and u.id_rol = 1
              and u.id_estado = 1
        )
    )
    WITH CHECK (
        exists (
            select 1 from public.usuarios u
            where u.id = auth.uid()
              and u.id_rol = 1
              and u.id_estado = 1
        )
    );

-- RLS en estado_mesa: solo SELECT para authenticated
ALTER TABLE public.estado_mesa ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "estado_mesa_select_authenticated" ON public.estado_mesa;
CREATE POLICY "estado_mesa_select_authenticated" ON public.estado_mesa
    FOR SELECT
    TO authenticated
    USING (true);

-- ──────────────────────────────────────────────────────────────────────────────
-- 7. Datos de prueba (8-10 mesas)
-- ──────────────────────────────────────────────────────────────────────────────

INSERT INTO public.mesa (numero_mesa, capacidad, ubicacion, id_estado, id_estado_mesa)
SELECT * FROM (VALUES
    (1, 2, 'Terraza', 1, 1),
    (2, 2, 'Terraza', 1, 1),
    (3, 4, 'Interior', 1, 2),
    (4, 4, 'Interior', 1, 1),
    (5, 6, 'Interior', 1, 3),
    (6, 4, 'Barra', 1, 1),
    (7, 2, 'Barra', 1, 2),
    (8, 8, 'Salón privado', 1, 1),
    (9, 4, 'Terraza', 1, 1),
    (10, 6, 'Interior', 1, 1)
) AS t(numero_mesa, capacidad, ubicacion, id_estado, id_estado_mesa)
WHERE NOT EXISTS (select 1 from public.mesa where numero_mesa = t.numero_mesa);

-- ============================================================================
-- FIN DE LA MIGRACIÓN
-- ============================================================================
