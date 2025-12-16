package com.example.proekt;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.proekt.network.RegisterResponse;
import com.example.proekt.network.RetrofitClient;
import com.example.proekt.utils.ActivityTransitionUtils;
import com.example.proekt.utils.WindowUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {
    EditText Username, Email, Password, ConfirmPassword;
    Button buttonRegister;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        WindowUtils.setupTransparentNavigationBar(this);

        Username = findViewById(R.id.Username);
        Email = findViewById(R.id.Email);
        Password = findViewById(R.id.Password);
        ConfirmPassword = findViewById(R.id.ConfirmPassword);
        buttonRegister = findViewById(R.id.buttonRegister);

        buttonRegister.setOnClickListener(v -> {
            String user = Username.getText().toString().trim();
            String email = Email.getText().toString().trim();
            String pass = Password.getText().toString().trim();
            String confirm = ConfirmPassword.getText().toString().trim();

            if (user.isEmpty() || email.isEmpty() || pass.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show();
            } else if (!pass.equals(confirm)) {
                Toast.makeText(this, "Пароли не совпадают", Toast.LENGTH_SHORT).show();
            } else {
                registerUser(user, email, pass);
            }
        });
    }
    private void registerUser(String username, String email, String password) {
        Call<RegisterResponse> call = RetrofitClient
                .getInstance()
                .getApi()
                .register(username, email, password);

        call.enqueue(new Callback<RegisterResponse>() {
            @Override
            public void onResponse(Call<RegisterResponse> call, Response<RegisterResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    RegisterResponse registerResponse = response.body();
                    if (registerResponse.isSuccess()) {
                        Toast.makeText(RegisterActivity.this, "Запись создана", Toast.LENGTH_SHORT).show();
                        ActivityTransitionUtils.finishWithSlideBack(RegisterActivity.this);
                    } else {
                        Toast.makeText(RegisterActivity.this, "Ошибка: " + registerResponse.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(RegisterActivity.this, "Ошибка сервера", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<RegisterResponse> call, Throwable t) {
                Toast.makeText(RegisterActivity.this, "Ошибка соединения: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
