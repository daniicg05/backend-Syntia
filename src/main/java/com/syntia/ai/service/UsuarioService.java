package com.syntia.ai.service;



import com.syntia.ai.model.Rol;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registra un nuevo usuario con la contraseña cifrada.
     *
     * @param email email del usuario
     * @param password contraseña en texto plano
     * @param rol rol del usuario
     * @return usuario creado
     * @throws IllegalStateException si el email ya está registrado
     */
    public Usuario registrar(String email, String password, Rol rol) {
        if (usuarioRepository.existsByEmail(email)) {
            throw new IllegalStateException("El email ya está registrado: " + email);
        }

        Usuario usuario = Usuario.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .rol(rol)
                .build();

        return usuarioRepository.save(usuario);
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

        usuario.setEmail(nuevoEmail);
        return usuarioRepository.save(usuario);
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
