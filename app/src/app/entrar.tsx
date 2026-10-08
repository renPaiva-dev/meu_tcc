import { Link, router } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text } from 'react-native';

import { ErroApi, mensagemDeErro } from '../api/cliente';
import { Aviso, CampoTexto, Paragrafo, Tela, Titulo } from '../componentes/basicos';
import { Botao } from '../componentes/Botao';
import { cores, fonte } from '../componentes/tema';
import { useSessao } from '../contexto/SessaoContexto';

export default function Entrar() {
  const { entrar } = useSessao();
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function aoEntrar() {
    setErro(null);
    if (!email.trim() || !senha) {
      setErro('Informe e-mail e senha.');
      return;
    }
    setEnviando(true);
    try {
      await entrar(email, senha);
      router.replace('/identificar');
    } catch (e) {
      setErro(e instanceof ErroApi && e.status === 401 ? 'E-mail ou senha incorretos.' : mensagemDeErro(e));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Tela>
      <Titulo>ReciclaTHE</Titulo>
      <Paragrafo>Descubra como descartar seus resíduos corretamente em Teresina.</Paragrafo>
      {erro ? <Aviso tipo="erro">{erro}</Aviso> : null}
      <CampoTexto
        rotulo="E-mail"
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        autoComplete="email"
        keyboardType="email-address"
        textContentType="emailAddress"
      />
      <CampoTexto
        rotulo="Senha"
        value={senha}
        onChangeText={setSenha}
        secureTextEntry
        autoComplete="password"
        textContentType="password"
        onSubmitEditing={aoEntrar}
      />
      <Botao titulo="Entrar" aoPressionar={aoEntrar} carregando={enviando} />
      <Botao titulo="Criar conta" variante="secundario" aoPressionar={() => router.push('/cadastro')} />
      <Link href="/privacidade" style={estilos.link} accessibilityRole="link">
        Como tratamos seus dados (privacidade)
      </Link>
      <Text style={estilos.rodape}>Projeto de TCC — dados de descarte de fontes públicas de Teresina-PI.</Text>
    </Tela>
  );
}

const estilos = StyleSheet.create({
  link: { color: cores.info, fontSize: fonte.normal, textDecorationLine: 'underline', paddingVertical: 12 },
  rodape: { color: cores.textoSecundario, fontSize: fonte.pequena },
});
