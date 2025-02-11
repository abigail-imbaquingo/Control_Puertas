package com.example.controlpuertas;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.google.firebase.database.*;

public class MainActivity extends AppCompatActivity {
    private EditText etUser, etPassword;
    private Button btnIngresar;
    private DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicialización de Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference();

        // Inicialización de elementos UI
        etUser = findViewById(R.id.etUser);
        etPassword = findViewById(R.id.etPassword);
        btnIngresar = findViewById(R.id.btnIngresar);

        btnIngresar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validarUsuario();
            }
        });
    }

    private void validarUsuario() {
        final String usuario = etUser.getText().toString();
        final String password = etPassword.getText().toString();

        if (usuario.isEmpty() || password.isEmpty()) {
            Toast.makeText(MainActivity.this, "Por favor complete todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validar credenciales en Firebase
        databaseReference.child("usuarios").child(usuario).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            String passwordGuardado = dataSnapshot.child("password").getValue(String.class);
                            if (password.equals(passwordGuardado)) {
                                // Login exitoso
                                Intent intent = new Intent(MainActivity.this, ControlActivity.class);
                                intent.putExtra("userId", usuario);
                                startActivity(intent);
                                finish();
                            } else {
                                Toast.makeText(MainActivity.this, "Contraseña incorrecta", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "Usuario no encontrado", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Toast.makeText(MainActivity.this, "Error de conexión", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}