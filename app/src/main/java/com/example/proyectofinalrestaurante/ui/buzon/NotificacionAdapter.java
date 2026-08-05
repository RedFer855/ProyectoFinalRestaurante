package com.example.proyectofinalrestaurante.ui.buzon;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.model.Notificacion;
import com.example.proyectofinalrestaurante.domain.model.TipoNotificacion;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Lista del buzón (Plan Fase 3, E9). El texto de cada aviso se <b>resuelve en la UI</b> a
 * partir del {@code tipo} + {@code arg1} — la base nunca guarda el texto (regla de oro #8 y
 * el error de P-019).
 */
public class NotificacionAdapter extends ListAdapter<Notificacion, NotificacionAdapter.Holder> {

    public interface AlTocar {
        void onTocar(Notificacion notificacion);
    }

    private final AlTocar alTocar;

    public NotificacionAdapter(AlTocar alTocar) {
        super(DIFF);
        this.alTocar = alTocar;
    }

    private static final DiffUtil.ItemCallback<Notificacion> DIFF =
            new DiffUtil.ItemCallback<Notificacion>() {
                @Override
                public boolean areItemsTheSame(@NonNull Notificacion a,
                                               @NonNull Notificacion b) {
                    return a.getIdLocal() == b.getIdLocal();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Notificacion a,
                                                  @NonNull Notificacion b) {
                    return a.isLeida() == b.isLeida() && a.getTipo() == b.getTipo();
                }
            };

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notificacion, parent, false);
        return new Holder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.enlazar(getItem(position), alTocar);
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final TextView texto;
        private final TextView hora;

        Holder(@NonNull View itemView) {
            super(itemView);
            texto = itemView.findViewById(R.id.txt_notificacion);
            hora = itemView.findViewById(R.id.txt_hora_notificacion);
        }

        void enlazar(Notificacion notificacion, AlTocar alTocar) {
            Context contexto = itemView.getContext();
            texto.setText(textoDe(contexto, notificacion));
            hora.setText(horaDe(contexto, notificacion.getCreadoEn()));
            itemView.setOnClickListener(v -> alTocar.onTocar(notificacion));
        }
    }

    private static String textoDe(Context contexto, @Nullable Notificacion notificacion) {
        if (notificacion == null) {
            return "";
        }
        if (notificacion.getTipo() == TipoNotificacion.PEDIDO_NUEVO) {
            return contexto.getString(R.string.notif_pedido_nuevo, notificacion.getArg1());
        }
        if (notificacion.getTipo() == TipoNotificacion.PEDIDO_LISTO) {
            return contexto.getString(R.string.notif_pedido_listo, notificacion.getArg1());
        }
        return contexto.getString(R.string.notif_error_sync);
    }

    private static String horaDe(Context contexto, long creadoEn) {
        DateFormat formato = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault());
        return formato.format(new Date(creadoEn));
    }
}