## Mudanças do Fork (Edição Otimizada)

Este fork aplica as seguintes otimizações de performance ao Smart Particles original:

### Melhorias de Performance

| Otimização | Antes | Depois | Impacto |
|---|---|---|---|
| Pass de cleanup | Segundo pass sobre todas as partículas | Eliminado - vanilla limpa partículas mortas automaticamente | Aprox. 40% menos CPU |
| Culling no caso comum | Heap scoring a cada tick | Frustum-only linear quando abaixo do limite | Mais rápido no gameplay normal |
| Cálculo de FOV | Recalculado com trigonometria todo tick | Cache - recalcula só quando FOV muda | Elimina trigonometria por tick |
| Acesso a campos da câmera | Dispatch virtual no inner loop | Variáveis locais finais | Loop mais otimizado |
| Memória do heap | Arrays nunca encolhem | Encolhem quando limite é reduzido | Menos desperdício de memória |
| Pressão no GC | Referências mortas retidas no array do heap | Limpas após cada tick | Menos pausas de GC |

### Compatibilidade

Totalmente compatível com Sodium, Iris, Lithium, FerriteCore, ModernFix e todos os principais mods de otimização.

**Não compatível com AsyncParticles** (conflito de acesso concorrente).

---

## Licença e Créditos

Este projeto é um fork do [Smart Particles](https://github.com/chedidandrew/Smart_Particles), criado originalmente por **chedidandrew**. Todo o crédito pelo conceito original e pela implementação base do mod é dele.

Este fork está licenciado sob a **Licença MIT**, a mesma do projeto original. Isso significa que você pode:

- Usar este mod em qualquer modpack, pessoal ou público;
- Ver, copiar e modificar o código-fonte livremente;
- Distribuir versões compiladas, desde que o aviso de licença original seja mantido.
-View, fork, and modify the source code.
- Distribute built versions (keeping the license intact).
