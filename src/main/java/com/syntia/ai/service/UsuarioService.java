package com.syntia.ai.service;



import com.syntia.ai.model.HistorialCorreo;
import com.syntia.ai.model.Rol;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.repository.HistorialCorreoRepository;
import com.syntia.ai.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

import java.util.List;
import java.util.Optional;

/**
 * Servicio de lógica de negocio para la gestión de usuarios.
 * Adaptado para API REST.
 */
@Service
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final HistorialCorreoRepository historialCorreoRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          HistorialCorreoRepository historialCorreoRepository,
                          PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.historialCorreoRepository = historialCorreoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Genera un token único para la verificación de cuenta.
     *
     * @return token aleatorio en formato UUID
     */
    private String generarToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Registra un nuevo usuario con contraseña cifrada y estado inicial no verificado.
     *
     * <p>Durante el alta se inicializan datos de verificación de cuenta:
     * <ul>
     *   <li>verified = false</li>
     *   <li>verificationToken = UUID aleatorio</li>
     *   <li>tokenExpiration = ahora + 24 horas</li>
     * </ul>
     *
     * @param email email del usuario
     * @param password contraseña en texto plano
     * @param rol rol del usuario
     * @return usuario creado y persistido
     * @throws IllegalStateException si el email ya está registrado
     */
    public Usuario registrar(String email, String password, Rol rol) {
        if (usuarioRepository.existsByEmail(email)) {
            throw new IllegalStateException("El email ya está registrado: " + email);
        }

        /** Inicializa la cuenta pendiente de verificación por token (24h de vigencia). */

        Usuario usuario = Usuario.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .rol(rol)

                .verified(false)
                .verificationToken(generarToken())
                .tokenExpiration(LocalDateTime.now().plusHours(24))
                .build();

        return usuarioRepository.save(usuario);
    }

    /**
     * Verifica una cuenta de usuario usando su token de verificación.
     *
     * <p>Reglas:
     * <ul>
     *   <li>Si el token no existe, lanza IllegalArgumentException("Token inválido").</li>
     *   <li>Si el token expiró, lanza IllegalArgumentException("Token expirado").</li>
     *   <li>Si es válido, marca verified=true y limpia token/expiración.</li>
     * </ul>
     *
     * @param token token de verificación recibido por query param
     * @throws IllegalArgumentException si el token es inválido o expirado
     */
    public void verificarToken(String token) {
        Usuario usuario = usuarioRepository.findByVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token inválido"));

        if (usuario.getTokenExpiration() == null
                || usuario.getTokenExpiration().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Token expirado");
        }

        usuario.setVerified(true);
        usuario.setVerificationToken(null);
        usuario.setTokenExpiration(null);

        usuarioRepository.save(usuario);
    }

    /**
     * Busca un usuario por email.
     *
     * @param email email del usuario
     * @return Optional con el usuario
     */
    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }

    /**
     * Obtiene todos los usuarios registrados.
     *
     * @return lista de usuarios
     */
    @Transactional(readOnly = true)
    public List<Usuario> obtenerTodos() {
        return usuarioRepository.findAll();
    }

    /**
     * Busca un usuario por ID.
     *
     * @param id ID del usuario
     * @return Optional con el usuario
     */
    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorId(Long id) {
        return usuarioRepository.findById(id);
    }

    /**
     * Obtiene un usuario por ID o lanza excepción si no existe.
     *
     * @param id ID del usuario
     * @return usuario encontrado
     */
    @Transactional(readOnly = true)
    public Usuario obtenerPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + id));
    }

    /**
     * Elimina un usuario por ID.
     *
     * @param id ID del usuario a eliminar
     * @throws EntityNotFoundException si el usuario no existe
     */
    public void eliminar(Long id) {
        if (!usuarioRepository.existsById(id)) {
            throw new EntityNotFoundException("Usuario no encontrado: " + id);
        }
        usuarioRepository.deleteById(id);
    }

    /**
     * Cambia el rol de un usuario.
     *
     * @param id ID del usuario
     * @param nuevoRol nuevo rol a asignar
     * @return usuario actualizado
     * @throws EntityNotFoundException si el usuario no existe
     */
    public Usuario cambiarRol(Long id, Rol nuevoRol) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + id));

        usuario.setRol(nuevoRol);
        return usuarioRepository.save(usuario);
    }

    /**
     * Cambia la contraseña de un usuario.
     *
     * @param id ID del usuario
     * @param nuevaPassword nueva contraseña en texto plano
     * @return usuario actualizado
     * @throws EntityNotFoundException si el usuario no existe
     */
    public Usuario cambiarPassword(Long id, String nuevaPassword) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + id));

        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        return usuarioRepository.save(usuario);
    }

    /**
     * Cambia el email de un usuario tras verificar su contraseña actual.
     *
     * @param id ID del usuario
     * @param passwordActual contraseña actual en texto plano (para verificación)
     * @param nuevoEmail nuevo email a asignar
     * @return usuario actualizado
     * @throws EntityNotFoundException si el usuario no existe
     * @throws IllegalArgumentException si la contraseña es incorrecta o el email es igual al actual
     * @throws IllegalStateException si el nuevo email ya está registrado
     */
    public Usuario cambiarEmail(Long id, String passwordActual, String nuevoEmail) {
        return cambiarEmail(id, passwordActual, nuevoEmail, null);
    }

    public Usuario cambiarEmail(Long id, String passwordActual, String nuevoEmail, String actorEmail) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + id));

        if (!passwordEncoder.matches(passwordActual, usuario.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta");
        }

        if (usuario.getEmail().equalsIgnoreCase(nuevoEmail)) {
            throw new IllegalArgumentException("El nuevo email es igual al actual");
        }

        if (usuarioRepository.existsByEmail(nuevoEmail)) {
            throw new IllegalStateException("El email ya está registrado: " + nuevoEmail);
        }

        String emailAnterior = usuario.getEmail();
        usuario.setEmail(nuevoEmail);
        Usuario actualizado = usuarioRepository.save(usuario);

        historialCorreoRepository.save(HistorialCorreo.builder()
                .usuario(actualizado)
                .anterior(emailAnterior)
                .nuevo(nuevoEmail)
                .actor(actorEmail != null && !actorEmail.isBlank() ? actorEmail : emailAnterior)
                .build());

        return actualizado;
    }

    @Transactional(readOnly = true)
    public boolean emailCambiado(Long usuarioId) {
        return historialCorreoRepository.existsByUsuarioId(usuarioId);
    }

    @Transactional(readOnly = true)
    public List<HistorialCorreo> obtenerHistorialCorreo(Long usuarioId) {
        return historialCorreoRepository.findByUsuarioIdOrderByFechaDesc(usuarioId);
    }

    /**
     * Cambia la contraseña de un usuario autenticado.
     *
     * @param usuarioId ID del usuario
     * @param passwordActual contraseña actual en texto plano (para verificación)
     * @param nuevaPassword nueva contraseña en texto plano
     * @param confirmarPassword confirmación de la nueva contraseña
     * @return usuario actualizado
     * @throws EntityNotFoundException si el usuario no existe
     * @throws BadCredentialsException si la contraseña actual es incorrecta
     * @throws IllegalArgumentException si la nueva contraseña y su confirmación no coinciden
     */
    public Usuario cambiarPasswordAutenticado(Long usuarioId,
                                              String passwordActual,
                                              String nuevaPassword,
                                              String confirmarPassword) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + usuarioId));

        if (!passwordEncoder.matches(passwordActual, usuario.getPassword())) {
            throw new BadCredentialsException("La contraseña actual es incorrecta");
        }

        if (!nuevaPassword.equals(confirmarPassword)) {
            throw new IllegalArgumentException("La nueva contraseña y su confirmación no coinciden");
        }

        if (passwordEncoder.matches(nuevaPassword, usuario.getPassword())) {
            throw new IllegalArgumentException("La nueva contraseña no puede ser igual a la actual");
        }

        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        return usuarioRepository.save(usuario);
    }
}
