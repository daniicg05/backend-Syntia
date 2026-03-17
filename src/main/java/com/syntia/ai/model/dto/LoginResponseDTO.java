package com.syntia.ai.model.dto;

// el loginrespondedto devuelve el token y el rol del usuario para que el frontend pueda mostrar la información adecuada
public class LoginResponseDTO {
    private String token;
    private String email;
    private String rol;
    private long expiresIn;

    public LoginResponseDTO(){} // constructor vacío para que jackson pueda deserializar la respuesta

    public LoginResponseDTO(String token, String email, String rol, long expiresIn) {
        this.token = token;
        this.email = email;
        this.rol = rol;
        this.expiresIn = expiresIn;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }
}
