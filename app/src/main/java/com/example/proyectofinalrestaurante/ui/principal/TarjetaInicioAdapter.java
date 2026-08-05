package com.example.proyectofinalrestaurante.ui.principal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.ui.comun.FormateadorFecha;

import java.util.Objects;

/**
 * Grilla de tarjetas del dashboard de Inicio (Plan Fase 3c, E10). Reemplaza el bucle de
 * inflado manual sobre {@code GridLayout} que tenía {@code InicioFragment} antes de esta fase.
 * Un toque navega al módulo de la tarjeta ({@link TarjetaInicio#getMenuId()}).
 */
public class TarjetaInicioAdapter extends ListAdapter<TarjetaInicio, TarjetaInicioAdapter.Holder> {

    public interface AlTocarTarjeta {
        void onTocar(@IdRes int menuId);
    }

    private final AlTocarTarjeta alTocarTarjeta;

    public TarjetaInicioAdapter(AlTocarTarjeta alTocarTarjeta) {
        super(DIFF);
        this.alTocarTarjeta = alTocarTarjeta;
    }

    private static final DiffUtil.ItemCallback<TarjetaInicio> DIFF =
            new DiffUtil.ItemCallback<TarjetaInicio>() {
        @Override
        public boolean areItemsTheSame(@NonNull TarjetaInicio a, @NonNull TarjetaInicio b) {
            return a.getTipo() == b.getTipo();
        }

        @Override
        public boolean areContentsTheSame(@NonNull TarjetaInicio a, @NonNull TarjetaInicio b) {
            return a.getValorPrincipal() == b.getValorPrincipal()
                    && a.getValorSecundario() == b.getValorSecundario()
                    && Objects.equals(a.getMontoOpcional(), b.getMontoOpcional())
                    && Objects.equals(a.getGeneradoEnOpcional(), b.getGeneradoEnOpcional());
        }
    };

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tarjeta_inicio, parent, false);
        return new Holder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.enlazar(getItem(position), alTocarTarjeta);
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final ImageView icono;
        private final TextView valor;
        private final TextView etiqueta;

        Holder(@NonNull View itemView) {
            super(itemView);
            icono = itemView.findViewById(R.id.img_icono_tarjeta);
            valor = itemView.findViewById(R.id.txt_valor_tarjeta);
            etiqueta = itemView.findViewById(R.id.txt_etiqueta_tarjeta);
        }

        void enlazar(TarjetaInicio tarjeta, AlTocarTarjeta alTocarTarjeta) {
            Context contexto = itemView.getContext();
            icono.setImageResource(TarjetaInicioUi.icono(tarjeta.getTipo()));
            valor.setText(formatearValor(contexto, tarjeta));
            etiqueta.setText(formatearEtiqueta(contexto, tarjeta));
            itemView.setOnClickListener(v -> alTocarTarjeta.onTocar(tarjeta.getMenuId()));
        }

        private String formatearValor(Context contexto, TarjetaInicio tarjeta) {
            switch (tarjeta.getTipo()) {
                case MESAS_OCUPADAS:
                    return contexto.getString(R.string.inicio_mesas_valor,
                            tarjeta.getValorPrincipal(), tarjeta.getValorSecundario());
                case VENTAS_HOY:
                    Double monto = tarjeta.getMontoOpcional();
                    // Nunca "L 0.00": decir "cero" cuando lo que pasa es "no sé" es el error
                    // más caro posible en un dashboard de ventas (Plan Fase 3c, §6).
                    return monto == null
                            ? contexto.getString(R.string.valor_no_disponible)
                            : contexto.getString(R.string.formato_lempiras, monto);
                default:
                    return String.valueOf(tarjeta.getValorPrincipal());
            }
        }

        private String formatearEtiqueta(Context contexto, TarjetaInicio tarjeta) {
            if (tarjeta.getTipo() != TarjetaInicio.Tipo.VENTAS_HOY) {
                return contexto.getString(TarjetaInicioUi.etiqueta(tarjeta.getTipo()));
            }
            String base = contexto.getString(R.string.inicio_ventas_hoy);
            Long generadoEn = tarjeta.getGeneradoEnOpcional();
            return generadoEn == null
                    ? contexto.getString(R.string.inicio_ventas_hoy_sin_conexion, base)
                    : contexto.getString(R.string.inicio_ventas_hoy_actualizado, base,
                            FormateadorFecha.horaCorta(generadoEn));
        }
    }
}
