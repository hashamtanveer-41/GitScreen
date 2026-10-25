# Contributing to GitScreen

Welcome! We are excited that you are interested in contributing to **GitScreen** as part of **Hacktoberfest** and beyond. This project is built on the spirit of open-source collaboration, learning, and building useful tools for the developer community.

---

## 🌟 How You Can Contribute

Whether you are fixing a typo, optimizing an algorithm, proposing a new feature, or adding test coverage, your contributions are welcome:

- 🐛 **Report Bugs**: Identify unexpected behavior, crashes, or device-specific layout quirks.
- 💡 **Suggest Features**: Propose visual diffing tools, branch export to ZIP/Git, or cloud backup plugins.
- 🛠️ **Submit Pull Requests**: Fix reported issues, improve code documentation, or enhance Compose UI animations.
- 📖 **Improve Documentation**: Enhance instructions, clarify setup steps, or share tutorials.

---

## 🚀 Getting Started with Development

### 1. Fork and Clone
```bash
git clone https://github.com/<your-username>/GitScreen.git
cd GitScreen
```

### 2. Open in Android Studio
- Open Android Studio (Ladybug 2024.2.1 or newer recommended).
- Allow Gradle sync to complete and download required dependencies.

### 3. Create a Branch
Always create a descriptive branch for your work:
```bash
git checkout -b feat/your-feature-name
# or
git checkout -b fix/issue-description
```

---

## 📐 Coding Standards & Guidelines

- **Kotlin Official Style**: Follow the [official Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- **Compose Best Practices**:
  - Keep Composables stateless and reusable where possible.
  - Hoist state up to ViewModels using `StateFlow`.
  - Use Material 3 semantic color tokens rather than hardcoded hex values.
- **Concurrency & Coroutines**:
  - Always use appropriate dispatchers (`Dispatchers.IO` for disk/database, `Dispatchers.Main` for UI).
  - Use `scanMutex` or thread-safe synchronization when modifying shared background state.
- **Conventional Commits**: Format commit messages cleanly:
  - `feat: add export version to zip feature`
  - `fix: prevent potential NPE in ForegroundAppDetector`
  - `docs: update setup instructions in README`

---

## 🔄 Pull Request (PR) Workflow

1. Ensure your code compiles cleanly:
   ```bash
   ./gradlew test
   ./gradlew assembleDebug
   ```
2. Push your branch to your GitHub fork:
   ```bash
   git push origin feat/your-feature-name
   ```
3. Open a Pull Request against `main` on the official repository.
4. Fill in the PR description detailing:
   - What changed and why.
   - Any relevant issue numbers.
   - Verification steps or screenshots of the change.

---

## 📜 Code of Conduct

All contributors are expected to uphold a welcoming, respectful, and inclusive environment. Please review our [Code of Conduct](CODE_OF_CONDUCT.md).

Thank you for contributing to open source!
