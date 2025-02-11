package com.example.controlpuertas;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class ControlActivity extends AppCompatActivity {
    private Button btnOpen, btnClose, btnCerrarSesion;
    private TextView lblEstado, txtFecha;
    private ImageView imgPuerta;
    private DatabaseReference databaseReference;
    private String userId;
    private Handler handler;
    private Runnable clockRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        // Obtener userId del Intent
        userId = getIntent().getStringExtra("userId");

        // Inicialización de Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference()
                .child("usuarios").child(userId);

        // Inicialización de elementos UI
        btnOpen = findViewById(R.id.btnOpen);
        btnClose = findViewById(R.id.btnClose);
        btnCerrarSesion = findViewById(R.id.btnCerrarSesion);
        lblEstado = findViewById(R.id.lblEstado);
        imgPuerta = findViewById(R.id.imgPuerta);
        txtFecha = findViewById(R.id.txtFecha);

        // Inicializar el handler para el reloj
        handler = new Handler();
        iniciarReloj();

        // Configurar listeners de botones
        btnOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controlarPuerta("Puerta Abierta");
            }
        });

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controlarPuerta("Puerta Cerrada");
            }
        });

        btnCerrarSesion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cerrarSesion();
            }
        });

        // Escuchar último estado de la puerta
        databaseReference.child("puerta").child("historial")
                .limitToLast(1)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            String estado = snapshot.child("estado").getValue(String.class);
                            if (estado != null) {
                                lblEstado.setText(estado);
                                actualizarImagenPuerta(estado);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Toast.makeText(ControlActivity.this,
                                "Error al leer estado", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void iniciarReloj() {
        clockRunnable = new Runnable() {
            @Override
            public void run() {
                actualizarFecha();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(clockRunnable);
    }

    private void actualizarFecha() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a",
                Locale.getDefault());
        txtFecha.setText(dateFormat.format(new Date()));
    }

    private void actualizarImagenPuerta(String estado) {
        if (estado.equals("Puerta Abierta")) {
            imgPuerta.setImageResource(R.drawable.puerta_abierta);
        } else {
            imgPuerta.setImageResource(R.drawable.puerta_cerrada);
        }
    }

    private void controlarPuerta(String estado) {
        String timestamp = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a",
                Locale.getDefault()).format(new Date());

        // Crear nuevo registro en el historial
        String nuevoRegistroKey = databaseReference
                .child("puerta")
                .child("historial")
                .push()
                .getKey();

        Map<String, Object> registroHistorial = new HashMap<>();
        registroHistorial.put("estado", estado);
        registroHistorial.put("timestamp", timestamp);
        registroHistorial.put("led", estado.equals("Puerta Abierta") ? 1 : 2);

        // Actualizar Firebase
        databaseReference
                .child("puerta")
                .child("historial")
                .child(nuevoRegistroKey)
                .setValue(registroHistorial)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(ControlActivity.this,
                            "Comando enviado exitosamente", Toast.LENGTH_SHORT).show();
                    actualizarImagenPuerta(estado);
                })
                .addOnFailureListener(e -> Toast.makeText(ControlActivity.this,
                        "Error al enviar comando", Toast.LENGTH_SHORT).show());
    }

    private void cerrarSesion() {
        // Detener el handler del reloj
        if (handler != null && clockRunnable != null) {
            handler.removeCallbacks(clockRunnable);
        }

        // Volver a la pantalla de login
        Intent intent = new Intent(ControlActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detener el handler cuando se destruye la actividad
        if (handler != null && clockRunnable != null) {
            handler.removeCallbacks(clockRunnable);
        }
    }
}