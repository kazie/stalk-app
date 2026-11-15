{
  description = "stalk-app dev shell";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" "aarch64-darwin" ];
      forAll = f: nixpkgs.lib.genAttrs systems (s: f nixpkgs.legacyPackages.${s});
    in {
      devShells = forAll (pkgs: {
        # Temurin 25 (sets JAVA_HOME) runs ./gradlew, same vendor as CI's
        # setup-java. Compiling still uses jvmToolchain(17), which Gradle fetches itself.
        # The Android SDK stays the one Android Studio manages (local.properties).
        default = pkgs.mkShell {
          packages = [ pkgs.temurin-bin-25 ];
          shellHook = ''
            if [ -z "''${ANDROID_HOME:-}" ] && [ -d "$HOME/Android/Sdk" ]; then
              export ANDROID_HOME="$HOME/Android/Sdk"
            fi
          '';
        };
      });
    };
}
