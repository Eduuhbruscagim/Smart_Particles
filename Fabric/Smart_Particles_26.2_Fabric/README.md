## Mudanças do Fork (Edição Otimizada)

Este fork aplica as seguintes otimizações de performance ao Smart Particles original:

### Melhorias de Performance

| Otimização | Antes | Depois | Impacto |
|---|---|---|---|
| Limpeza de partículas | Segundo passe completo sobre todas as partículas | Eliminado, o próprio Minecraft limpa partículas mortas automaticamente | Aproximadamente 40% menos uso de CPU |
| Culling no caso comum | Classificação via heap a cada tick | Apenas culling de frustum linear quando abaixo do limite | Mais rápido durante gameplay normal |
| Cálculo de campo de visão | Recalculado com trigonometria todo tick | Usa cache - recalcula só quando o campo de visão muda | Elimina cálculos desnecessários por tick |
| Acesso a campos da câmera | Acesso virtual repetido no loop interno | Variáveis locais finais | Loop interno mais otimizado |
| Memória do heap | Arrays nunca encolhem | Encolhem quando o limite de partículas é reduzido | Menos desperdício de memória |
| Pressão no coletor de lixo | Referências mortas retidas no array do heap | Limpas após cada tick | Menos pausas do coletor de lixo |

### Compatibilidade

Totalmente compatível com Sodium, Iris, Lithium, FerriteCore, ModernFix e todos os principais mods de otimização.

Não compatível com AsyncParticles devido a conflito de acesso concorrente.

---

## Licença e Créditos

Este projeto é um fork do [Smart Particles](https://github.com/chedidandrew/Smart_Particles), criado originalmente por **chedidandrew**. Todo o crédito pelo conceito original e pela implementação base do mod é dele.

Este fork está licenciado sob a **Licença MIT**, a mesma do projeto original. Isso significa que você pode:

- Usar este mod em qualquer modpack, pessoal ou público;
- Ver, copiar e modificar o código-fonte livremente;
- Distribuir versões compiladas, desde que o aviso de licença original seja mantido.
