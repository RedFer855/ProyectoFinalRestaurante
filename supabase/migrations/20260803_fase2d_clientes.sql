-- ============================================================
-- Fase 2d — CRUD de Clientes
-- Migración SQL para Supabase (Postgres 17.6)
-- ============================================================
-- Esta migración asume que la tabla `clientes` ya existe con
-- al menos: id_cliente, nombre, apellido, identidad.
-- Si falta alguna columna, se agrega con IF NOT EXISTS.
-- ============================================================

-- 2.1 Columnas nuevas
alter table public.clientes
    add column if not exists actualizado_en timestamptz not null default now();

-- id_estado para baja lógica (Activo/Inactivo)
alter table public.clientes
    add column if not exists id_estado int not null default 1;

-- telefono si no existe
alter table public.clientes
    add column if not exists telefono varchar(20) null;

-- 2.2 Normalización de identidad — único sobre identidad normalizada
-- Solo aplica cuando identidad no es NULL (venta de mostrador sin identidad no colisiona).
drop index if exists uq_clientes_identidad;
create unique index if not exists uq_clientes_identidad_norm
    on public.clientes (regexp_replace(identidad, '[^0-9]', '', 'g'))
    where identidad is not null;

-- 2.3 Triggers

-- Trigger: actualizar actualizado_en BEFORE UPDATE
create or replace function public.tocar_actualizado_en_clientes()
returns trigger
language plpgsql
as $$
begin
    new.actualizado_en = now();
    return new;
end;
$$;

drop trigger if exists trg_clientes_actualizado_en on public.clientes;
create trigger trg_clientes_actualizado_en
    before update on public.clientes
    for each row
    execute function public.tocar_actualizado_en_clientes();

-- Trigger: evitar DELETE si el cliente tiene pedidos
create or replace function public.trg_clientes_no_borrar_con_pedidos_fn()
returns trigger
language plpgsql
as $$
begin
    -- Válvula de escape para reparación desde SQL Editor
    if auth.uid() is null then
        return old;
    end if;
    if exists (select 1 from public.pedido p where p.id_cliente = old.id_cliente) then
        raise exception 'No se puede borrar un cliente que ya tiene pedidos. Dalo de baja en su lugar.';
    end if;
    return old;
end;
$$;

drop trigger if exists trg_clientes_no_borrar_con_pedidos on public.clientes;
create trigger trg_clientes_no_borrar_con_pedidos
    before delete on public.clientes
    for each row
    execute function public.trg_clientes_no_borrar_con_pedidos_fn();

-- Revocar EXECUTE de las funciones de trigger a anon y authenticated
revoke execute on function public.tocar_actualizado_en_clientes() from anon;
revoke execute on function public.tocar_actualizado_en_clientes() from authenticated;
revoke execute on function public.trg_clientes_no_borrar_con_pedidos_fn() from anon;
revoke execute on function public.trg_clientes_no_borrar_con_pedidos_fn() from authenticated;

-- 2.4 RPC: buscar_o_crear_cliente
create or replace function public.buscar_o_crear_cliente(
    p_nombre    varchar,
    p_apellido  varchar,
    p_identidad varchar default null,
    p_telefono  varchar default null
) returns int
language plpgsql
security definer
set search_path = public
as $$
declare
    v_id_cliente int;
    v_norm       varchar;
begin
    if rol_actual() not in ('admin', 'mesero') then
        raise exception 'No tenés permiso para registrar clientes.';
    end if;
    if coalesce(btrim(p_nombre), '') = '' or coalesce(btrim(p_apellido), '') = '' then
        raise exception 'El nombre y el apellido del cliente son obligatorios.';
    end if;

    v_norm := nullif(regexp_replace(coalesce(p_identidad, ''), '[^0-9]', '', 'g'), '');

    if v_norm is not null then
        select id_cliente into v_id_cliente
          from public.clientes
         where regexp_replace(identidad, '[^0-9]', '', 'g') = v_norm
         limit 1;

        if found then
            return v_id_cliente;
        end if;
    end if;

    insert into public.clientes (nombre, apellido, identidad, telefono)
    values (btrim(p_nombre), btrim(p_apellido), nullif(btrim(p_identidad), ''), nullif(btrim(p_telefono), ''))
    returning id_cliente into v_id_cliente;

    return v_id_cliente;
end;
$$;

revoke execute on function public.buscar_o_crear_cliente(varchar, varchar, varchar, varchar) from anon;
grant  execute on function public.buscar_o_crear_cliente(varchar, varchar, varchar, varchar) to authenticated;

-- 2.5 Vista de lectura
create or replace view public.vista_clientes
with (security_invoker = on) as
select c.id_cliente,
       c.nombre,
       c.apellido,
       c.identidad,
       c.telefono,
       c.id_estado,
       (c.id_estado = 1) as activo,
       (select count(*) from public.pedido p where p.id_cliente = c.id_cliente) as cantidad_pedidos,
       c.actualizado_en
  from public.clientes c;

-- 2.6 RLS
-- SELECT: solo admin y mesero (cocina no ve datos personales)
-- INSERT/UPDATE: admin y mesero
-- DELETE: solo admin (el trigger lo bloquea si hay pedidos)

-- Asegurar que anon no puede leer clientes
alter table public.clientes enable row level security;

drop policy if exists clientes_select_admin_mesero on public.clientes;
create policy clientes_select_admin_mesero on public.clientes
    for select
    using (rol_actual() in ('admin', 'mesero'));

drop policy if exists clientes_insert_admin_mesero on public.clientes;
create policy clientes_insert_admin_mesero on public.clientes
    for insert
    with check (rol_actual() in ('admin', 'mesero'));

drop policy if exists clientes_update_admin_mesero on public.clientes;
create policy clientes_update_admin_mesero on public.clientes
    for update
    using (rol_actual() in ('admin', 'mesero'));

drop policy if exists clientes_delete_admin on public.clientes;
create policy clientes_delete_admin on public.clientes
    for delete
    using (rol_actual() = 'admin');

-- 2.8 Datos de prueba
insert into public.clientes (nombre, apellido, identidad, telefono, id_estado)
select v nombre, v apellido, v identidad, v telefono, 1
from (values
    ('Ana', 'Cruz', '0801199512345', '9988-1122'),
    ('Luis', 'Medina', '0501200203344', '3344-5566'),
    ('Sofía', 'Ramos', '0801198877665', '9911-2233'),
    ('Carlos', 'Núñez', '0703199044556', '8877-6655'),
    ('Gabriela', 'Paz', null, '9900-1234')
) as t(v nombre, v apellido, v identidad, v telefono)
where not exists (select 1 from public.clientes c where c.nombre = t.v nombre and c.apellido = t.v apellido);
