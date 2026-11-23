package br.com.debeatur.api.acesso;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * O admin muda o papel dos usuários do escritório e os desativa ou reativa. Ninguém muda o
 * próprio acesso, e a mudança só vale se quem a pede ainda é admin ativo quando ela é gravada:
 * assim o escritório sempre fica com ao menos um admin, mesmo com dois admins se rebaixando ao
 * mesmo tempo.
 */
@Service
class GestaoDeUsuarios {

    private final Usuarios usuarios;

    GestaoDeUsuarios(Usuarios usuarios) {
        this.usuarios = usuarios;
    }

    List<Usuarios.Usuario> listar(UsuarioAutenticado admin) {
        return usuarios.listar(admin.escritorio());
    }

    /**
     * @param papel nulo para manter o atual
     * @param ativo nulo para manter o atual
     */
    @Transactional
    Usuarios.Usuario alterar(UsuarioAutenticado admin, UUID id, Papel papel, Boolean ativo) {
        if (admin.id().equals(id)) {
            throw new ProprioAcessoException();
        }
        usuarios.travarEscritorio(admin.escritorio());
        Usuarios.Usuario quemPede = usuarios.buscar(admin.escritorio(), admin.id()).orElseThrow(SemPermissaoException::new);
        if (quemPede.papel() != Papel.ADMIN || !quemPede.ativo()) {
            throw new SemPermissaoException();
        }
        Usuarios.Usuario atual = usuarios.buscar(admin.escritorio(), id).orElseThrow(() -> new UsuarioNaoEncontradoException(id));
        Papel novoPapel = papel == null ? atual.papel() : papel;
        boolean novoAtivo = ativo == null ? atual.ativo() : ativo;
        usuarios.alterar(id, novoPapel, novoAtivo);
        if (novoPapel != Papel.ADMIN || !novoAtivo) {
            // quem perdeu o admin não deixa convite valendo
            usuarios.cancelarConvitesDe(id);
        }
        return new Usuarios.Usuario(atual.id(), atual.email(), novoPapel, novoAtivo, atual.criadoEm());
    }

    static class UsuarioNaoEncontradoException extends RuntimeException {

        UsuarioNaoEncontradoException(UUID id) {
            super("Usuário " + id + " não encontrado");
        }
    }

    static class ProprioAcessoException extends RuntimeException {

        ProprioAcessoException() {
            super("Você não pode mudar o seu próprio papel nem se desativar. Peça a outro admin do escritório.");
        }
    }

    /** Quem pediu deixou de ser admin, ou foi desativado, entre a autorização e a gravação. */
    static class SemPermissaoException extends RuntimeException {

        SemPermissaoException() {
            super("Só um admin ativo do escritório pode mudar o acesso dos usuários.");
        }
    }
}
