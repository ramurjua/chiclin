# -*- coding: utf-8 -*-
"""
chiclin.py — Entry point de la aplicación.

Modo por defecto : interfaz gráfica (PyQt5).
Modo legacy      : python chiclin.py --cli

Comando para generar el exe:
    pyinstaller --onefile --icon=money_face.ico chiclin.py
"""

import sys


# ======================
# MODO GRÁFICO (default)
# ======================

def run_gui():
    from PyQt5 import QtWidgets
    from app import MainWindow

    app = QtWidgets.QApplication(sys.argv)
    window = MainWindow()
    window.show()
    sys.exit(app.exec_())


# ======================
# MODO CONSOLA (legacy)
# ======================

def run_cli():
    """
    Modo consola heredado. Mantiene toda la funcionalidad original:
    añadir gastos/ingresos/ahorros, ver estadísticas,
    cambiar de mes y comparar meses.
    """
    import matplotlib.pyplot as plt
    from collections import defaultdict
    from logic import FinanceManager, MONTHS_ES

    fm = FinanceManager()

    # --- Helpers de presentación (solo CLI) ---

    def _print_aggregated(title, data_dict):
        agg = fm._aggregate(data_dict)
        print(f"\n{title}")
        print("-" * len(title))
        for label, total in agg.items():
            print(f"  {label}: {total:.2f} €")

    def show_statistics():
        s = fm.get_summary()
        print("\nRESUMEN FINANCIERO")
        print("------------------")
        print(f"Ingresos totales: {s['total_ingresos']:.2f} €")
        print(f"Gastos totales:   {s['total_gastos']:.2f} €")
        if s["balance"] >= 0:
            print(f"Ahorro potencial: {s['balance']:.2f} €")
        else:
            print("⚠️  Gastas más de lo que ingresas")

        _print_aggregated("Resumen de gastos",   fm.gastos)
        _print_aggregated("Resumen de ingresos", fm.ingresos)
        _print_aggregated("Resumen de ahorros",  fm.ahorros)

        if s["total_ingresos"] > 0 and s["gastos_agg"]:
            print("\nGastos como % del ingreso total:")
            for nombre, valor in s["gastos_agg"].items():
                print(f"  - {nombre}: {(valor / s['total_ingresos']) * 100:.2f}%")

        # Gráfico barras
        if s["total_ingresos"] > 0 or s["total_gastos"] > 0:
            plt.figure()
            plt.bar(["Ingresos", "Gastos"], [s["total_ingresos"], s["total_gastos"]],
                    color=["#4caf50", "#f44336"])
            plt.title("Ingresos vs Gastos")
            plt.ylabel("€")
            plt.show()

        # Tarta de gastos sobre ingresos
        pie = fm.get_pie_data()
        if pie["valores"]:
            plt.figure()
            plt.pie(pie["valores"], autopct=fm.autopct_if_big_enough, startangle=90)
            plt.title("Gastos como % del ingreso total")
            plt.legend(pie["etiquetas"], loc="center left", bbox_to_anchor=(1, 0.5))
            plt.show()

        # Tarta solo gastos
        if s["gastos_agg"]:
            plt.figure()
            plt.pie(s["gastos_agg"].values(),
                    autopct=fm.autopct_if_big_enough, startangle=90)
            plt.title("Distribución de gastos")
            plt.legend(s["gastos_agg"].keys(), loc="center left", bbox_to_anchor=(1, 0.5))
            plt.show()

    def select_month():
        """Pregunta qué mes cargar al arrancar. Si no hay meses anteriores, carga el actual sin preguntar."""
        previous = fm.list_available_months(exclude_current=True)

        if not previous:
            return fm.get_month_filename()

        print("\n¿QUÉ MES DESEA USAR?")
        print("  1 - Mes actual")
        print("  2 - Mes anterior")
        opt = input("Seleccione opción: ")

        if opt == "1":
            return fm.get_month_filename()
        elif opt == "2":
            print("\nMeses anteriores disponibles:")
            for i, (dt, _) in enumerate(previous):
                print(f"  {i} - {MONTHS_ES[dt.month]} {dt.year}")
            try:
                idx = int(input("Seleccione mes: "))
                return previous[idx][1]
            except (ValueError, IndexError):
                print("Índice no válido, cargando mes actual.")
                return fm.get_month_filename()
        else:
            print("Opción no válida, cargando mes actual.")
            return fm.get_month_filename()

    def change_month():
        previous = fm.list_available_months(exclude_current=True)

        print("\nCAMBIAR DE MES")
        print("  1 - Mes actual")
        if previous:
            print("  2 - Mes anterior")
        opt = input("Seleccione opción: ")

        if opt == "1":
            filename = fm.get_month_filename()
        elif opt == "2" and previous:
            print("\nMeses disponibles:")
            for i, (dt, _) in enumerate(previous):
                print(f"  {i} - {MONTHS_ES[dt.month]} {dt.year}")
            try:
                idx = int(input("Seleccione mes: "))
                filename = previous[idx][1]
            except (ValueError, IndexError):
                print("Índice no válido.")
                return
        else:
            print("Opción no válida.")
            return

        fm.switch_month(filename)
        print(f"Mes activo: {fm.current_month_label}")

    def comparar_meses_cli():
        months = fm.list_available_months()
        if len(months) < 2:
            print("\nNecesitas al menos 2 meses con datos.")
            return

        print("\nMeses disponibles:")
        for i, (dt, _) in enumerate(months):
            print(f"{i} - {MONTHS_ES[dt.month]} {dt.year}")

        print("\n1 - Comparar 2 meses específicos")
        print("2 - Comparar todos los meses (tendencia)")
        print("3 - Gastos recurrentes")
        opt = input("Seleccione opción: ")

        if opt == "1":
            idx1 = int(input("Primer mes (número): "))
            idx2 = int(input("Segundo mes (número): "))
            r = fm.comparar_dos_meses(months[idx1][1], months[idx2][1])

            print(f"\n{'='*60}")
            print(f"COMPARACIÓN: {r['mes1']} vs {r['mes2']}")
            print(f"{'='*60}")
            print(f"\n{r['mes1']}: ingresos {r['total_ingresos1']:.2f} | gastos {r['total_gastos1']:.2f}")
            print(f"{r['mes2']}: ingresos {r['total_ingresos2']:.2f} | gastos {r['total_gastos2']:.2f}")
            print(f"\nCOMPARACIÓN POR CATEGORÍA:")
            print("-" * 60)
            for d in r["detalle"]:
                if d["estado"] == "NUEVO":
                    print(f"{d['cat']:20} | NUEVO → {d['val2']:.2f} €")
                elif d["estado"] == "ELIMINADO":
                    print(f"{d['cat']:20} | {d['val1']:.2f} € → ELIMINADO")
                else:
                    pct = f"{d['pct_cambio']:+.1f}%" if d["pct_cambio"] is not None else ""
                    print(f"{d['cat']:20} | {d['val1']:8.2f} → {d['val2']:8.2f} {d['estado']} {pct}")

            # Gráfico
            if r["categorias"]:
                fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 6))
                x = range(len(r["categorias"]))
                w = 0.35
                ax1.bar([i - w/2 for i in x], r["valores1"], w, label=r["mes1"])
                ax1.bar([i + w/2 for i in x], r["valores2"], w, label=r["mes2"])
                ax1.set_xticks(x)
                ax1.set_xticklabels(r["categorias"], rotation=45, ha="right")
                ax1.set_title("Gastos por categoría")
                ax1.legend()
                ax1.grid(axis="y", alpha=0.3)
                ax2.bar([r["mes1"], r["mes2"]], [r["total_gastos1"], r["total_gastos2"]],
                        color=["#3498db", "#e74c3c"])
                ax2.set_title("Gastos totales")
                ax2.grid(axis="y", alpha=0.3)
                plt.tight_layout()
                plt.show()

        elif opt == "2":
            datos = fm.comparar_todos_meses()
            print(f"\n{'Mes':20} | {'Ingresos':>10} | {'Gastos':>10} | {'Balance':>10}")
            print("-" * 60)
            for m in datos:
                print(f"{m['nombre']:20} | {m['total_ingresos']:10.2f} | "
                      f"{m['total_gastos']:10.2f} | {m['balance']:10.2f}")

            nombres = [m["nombre"] for m in datos]
            fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 8))
            ax1.plot(nombres, [m["total_ingresos"] for m in datos], marker="o", label="Ingresos")
            ax1.plot(nombres, [m["total_gastos"]   for m in datos], marker="o", label="Gastos")
            ax1.set_title("Evolución ingresos y gastos")
            ax1.legend()
            ax1.grid(True, alpha=0.3)
            ax1.tick_params(axis="x", rotation=45)
            balances = [m["balance"] for m in datos]
            colores  = ["green" if b >= 0 else "red" for b in balances]
            ax2.bar(nombres, balances, color=colores, alpha=0.7)
            ax2.axhline(0, color="black", linewidth=0.5)
            ax2.set_title("Balance mensual")
            ax2.grid(axis="y", alpha=0.3)
            ax2.tick_params(axis="x", rotation=45)
            plt.tight_layout()
            plt.show()

        elif opt == "3":
            r = fm.analizar_gastos_recurrentes()
            print(f"\nGASTOS FIJOS ({r['total_meses']} meses):")
            for cat, aps in r["fijos"]:
                vals = [a["valor"] for a in aps]
                print(f"  {cat}: media {sum(vals)/len(vals):.2f} €")
            print(f"\nGASTOS RECURRENTES:")
            for cat, aps in r["recurrentes"]:
                vals = [a["valor"] for a in aps]
                print(f"  {cat} ({len(aps)}/{r['total_meses']} meses): media {sum(vals)/len(vals):.2f} €")
        else:
            print("Opción no válida.")

    def manage():
        opt = input(
            "\n0 - Añadir gasto\n"
            "1 - Añadir ingreso\n"
            "2 - Añadir ahorro\n"
            "3 - Ver estadísticas\n"
            "4 - Cambiar de mes\n"
            "5 - Comparar meses\n"
            "Seleccione: "
        )

        if opt == "3":
            show_statistics()
            return "continue"
        if opt == "4":
            change_month()
            return "skip_confirm"
        if opt == "5":
            comparar_meses_cli()
            return "continue"

        tipo_map = {"0": "gastos", "1": "ingresos", "2": "ahorros"}
        if opt not in tipo_map:
            print("Opción no válida.")
            return "continue"

        label = input("Nombre: ")
        try:
            value = float(input("Valor: "))
        except ValueError:
            print("Error: introduce un número válido.")
            return "continue"

        try:
            fm.add_entry(label, value, tipo_map[opt])
        except ValueError as e:
            print(f"Error: {e}")

        return "continue"

    # --- Bucle principal CLI ---

    fm.load_month(select_month())
    print(f"Mes activo: {fm.current_month_label}")

    while True:
        action = manage()
        if action == "skip_confirm":
            continue
        ans = input("¿Desea realizar otra operación? (y/n): ").lower()
        if ans != "y":
            break

    fm.save()
    print("Datos guardados. ¡Hasta luego!")


# ======================
# ENTRY POINT
# ======================

if __name__ == "__main__":
    if "--cli" in sys.argv:
        run_cli()
    else:
        run_gui()