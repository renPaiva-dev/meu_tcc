import { router } from 'expo-router';
import { useState } from 'react';

import { ErroApi, mensagemDeErro } from '../api/cliente';
import { AvisoPrivacidade } from '../componentes/AvisoPrivacidade';
import { Aviso, CampoTexto, Paragrafo, Subtitulo, Tela } from '../componentes/basicos';
import { Botao } from '../componentes/Botao';
import { SeletorZona } from '../componentes/seletores';
import { useDados } from '../contexto/DadosContexto';
import { useSessao } from '../contexto/SessaoContexto';

type Erros = Partial<Record<'nome' | 'email' | 'senha' | 'zona', string>>;

export default function Cadastro() {
  const { cadastrar } = useSessao();
  const { zonas } = useDados();
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [zona, setZona] = useState<string | null>(null);
  const [erros, setErros] = useState<Erros>({});
  const [erroGeral, setErroGeral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  function validar(): Erros {
    const e: Erros = {};
    if (!nome.trim()) e.nome = 'Informe seu nome.';
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) e.email = 'E-mail inválido.';
    if (senha.length < 8) e.senha = 'A senha deve ter pelo menos 8 caracteres.';
    return e;
  }

  async function aoCadastrar() {
    setErroGeral(null);
    const e = validar();
    setErros(e);
    if (Object.keys(e).length > 0) return;
    setEnviando(true);
    try {
      await cadastrar({ nome: nome.trim(), email, senha, zona });
      router.replace('/identificar');
    } catch (err) {
      if (err instanceof ErroApi && Object.keys(err.campos).length > 0) {
        setErros(err.campos as Erros);
        setErroGeral('Corrija os campos destacados.');
      } else {
        setErroGeral(mensagemDeErro(err));
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Tela>
      {erroGeral ? <Aviso tipo="erro">{erroGeral}</Aviso> : null}
      <CampoTexto rotulo="Nome" value={nome} onChangeText={setNome} autoComplete="name" erro={erros.nome} />
      <CampoTexto
        rotulo="E-mail"
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        keyboardType="email-address"
        autoComplete="email"
        erro={erros.email}
      />
      <CampoTexto
        rotulo="Senha (mínimo 8 caracteres)"
        value={senha}
        onChangeText={setSenha}
        secureTextEntry
        autoComplete="new-password"
        erro={erros.senha}
      />
      <Subtitulo>Sua zona de Teresina</Subtitulo>
      <Paragrafo secundario>
        Usada para mostrar o telefone do Disk Coleta da sua região e o ranking da zona. Você pode mudar depois.
      </Paragrafo>
      <SeletorZona zonas={zonas} valor={zona} aoMudar={setZona} />
      {erros.zona ? <Aviso tipo="erro">{erros.zona}</Aviso> : null}
      <AvisoPrivacidade />
      <Botao titulo="Criar conta" aoPressionar={aoCadastrar} carregando={enviando} />
    </Tela>
  );
}
