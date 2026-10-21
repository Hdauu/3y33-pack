# 3y33Login

Plugin propio de 3y33 SMP: titulos en pantalla para el login de AuthMe.

- `src/`: codigo del plugin.
- `stubs/`: clases minimas de Paper/AuthMe solo para compilar (no van en el jar).
- Compilar: `javac --release 21 -d stubs-out $(find stubs -name "*.java")` y luego
  `javac --release 21 -encoding UTF-8 -cp stubs-out -d out src/ar/com/tresyetres/login/TresLogin.java`,
  copiar `plugin.yml` y `config.yml` a `out/` y armar el jar.
