# -*- coding: utf-8 -*-
"""
app.py — Ventana principal de Chiclin (modo gráfico).

Consume FinanceManager de logic.py para toda la lógica.
No contiene nada de negocio: solo presentación y eventos Qt.
"""

import sys
from datetime import datetime

import matplotlib
matplotlib.use("Qt5Agg")
from matplotlib.backends.backend_qt5agg import FigureCanvasQTAgg as FigureCanvas
from matplotlib.figure import Figure

from PyQt5 import QtCore, QtWidgets
from PyQt5.QtWidgets import QMessageBox

from ui_main import Ui_MainWindow
from logic import FinanceManager, MONTHS_ES


class MainWindow(QtWidgets.QMainWindow, Ui_MainWindow):

    def __init__(self):
        super().__init__()
        self.setupUi(self)

        self.fm = FinanceManager()
        self.fm.load_current_month()
        self._update_title()

        # Pestaña 1
        self._setup_tab_mes()

        # Pestaña 2
        self._setup_tab_comparacion()

        # Señales
        self._connect_signals()

        self.refresh_mes()

    # ═══════════════════════════════════════════════════════════════════════
    # PESTAÑA 1 — Mes actual
    # ═══════════════════════════════════════════════════════════════════════

    def _setup_tab_mes(self):
        self.NameText.setPlaceholderText("Nombre del gasto / ingreso")
        self.ValueText.setPlaceholderText("Valor del gasto / ingreso")
        self.NameText.clear()
        self.ValueText.clear()
        self.radioButton.setChecked(True)

        self._fig_pie, self._canvas_pie, self._ax_pie = self._make_canvas(self.Gastos_Graph)
        self._fig_bar, self._canvas_bar, self._ax_bar = self._make_canvas(self.GastosIngresos_Graph)
        self._pie_hover_cid = None

        self._populate_month_combo()

    def _populate_month_combo(self):
        self.mes_combo.blockSignals(True)
        self.mes_combo.clear()

        months = self.fm.list_available_months()
        current_fname = self.fm.get_month_filename()
        if current_fname not in [f for _, f in months]:
            ahora = datetime.now()
            months.append((datetime(ahora.year, ahora.month, 1), current_fname))
            months.sort(key=lambda x: x[0])

        self._month_list = months
        for dt, fname in months:
            self.mes_combo.addItem(f"{MONTHS_ES[dt.month]} {dt.year}", userData=fname)

        for i, (_, fname) in enumerate(months):
            if fname == self.fm.current_filename:
                self.mes_combo.setCurrentIndex(i)
                break

        self.mes_combo.blockSignals(False)

    def _on_month_changed(self, index: int):
        if index < 0 or index >= len(self._month_list):
            return
        _, fname = self._month_list[index]
        self.fm.switch_month(fname)
        self._update_title()
        self.refresh_mes()

    def _on_ok_clicked(self):
        nombre    = self.NameText.toPlainText().strip()
        valor_str = self.ValueText.toPlainText().strip()

        if not nombre:
            QMessageBox.warning(self, "Error", "Introduce un nombre.")
            return
        try:
            valor = float(valor_str.replace(",", "."))
        except ValueError:
            QMessageBox.warning(self, "Error", f'"{valor_str}" no es un número válido.')
            return

        tipo = "gastos" if self.radioButton.isChecked() else \
               "ingresos" if self.radioButton_2.isChecked() else None
        if tipo is None:
            QMessageBox.warning(self, "Error", "Selecciona Gasto o Ingreso.")
            return

        try:
            self.fm.add_entry(nombre, valor, tipo)
        except ValueError as e:
            QMessageBox.warning(self, "Error", str(e))
            return

        self._populate_month_combo()
        self.NameText.clear()
        self.ValueText.clear()
        self.refresh_mes()

    def refresh_mes(self):
        self._draw_pie()
        self._draw_bar()
        self._update_resumen()

    def _draw_pie(self):
        ax = self._ax_pie
        ax.clear()

        if hasattr(self, "_pie_hover_cid") and self._pie_hover_cid:
            self._canvas_pie.mpl_disconnect(self._pie_hover_cid)
            self._pie_hover_cid = None

        gastos_agg = self.fm.get_summary()["gastos_agg"]

        if not gastos_agg:
            ax.text(0.5, 0.5, "Sin gastos aún", ha="center", va="center",
                    fontsize=13, color="gray", transform=ax.transAxes)
            ax.set_title("Distribución de gastos", fontsize=11)
            self._fit_canvas(self.Gastos_Graph, self._canvas_pie, self._fig_pie)
            return

        valores   = list(gastos_agg.values())
        etiquetas = list(gastos_agg.keys())
        total     = sum(valores)

        wedges, _, _ = ax.pie(
            valores, autopct=FinanceManager.autopct_if_big_enough,
            startangle=90, pctdistance=0.75,
        )
        ax.legend(wedges, etiquetas, loc="center left",
                  bbox_to_anchor=(1.0, 0.5), fontsize=8)
        ax.set_title("Distribución de gastos", fontsize=11)

        annot = ax.annotate(
            "", xy=(0, 0), xytext=(20, 20), textcoords="offset points",
            bbox=dict(boxstyle="round,pad=0.4", fc="white", ec="gray", alpha=0.92),
            fontsize=10, arrowprops=dict(arrowstyle="->", color="gray"),
        )
        annot.set_visible(False)

        def on_hover(event):
            if event.inaxes != ax:
                annot.set_visible(False)
                self._canvas_pie.draw_idle()
                return
            visible = False
            for wedge, label, valor in zip(wedges, etiquetas, valores):
                if wedge.contains_point([event.x, event.y]):
                    annot.set_text(f"{label}\n{valor:.2f} € ({valor/total*100:.1f}%)")
                    annot.xy = (event.xdata, event.ydata)
                    annot.set_visible(True)
                    visible = True
                    break
            if not visible:
                annot.set_visible(False)
            self._canvas_pie.draw_idle()

        self._pie_hover_cid = self._canvas_pie.mpl_connect("motion_notify_event", on_hover)
        self._fit_canvas(self.Gastos_Graph, self._canvas_pie, self._fig_pie)

    def _draw_bar(self):
        ax = self._ax_bar
        ax.clear()

        s = self.fm.get_bar_data()
        ti, tg = s["total_ingresos"], s["total_gastos"]

        if ti == 0 and tg == 0:
            ax.text(0.5, 0.5, "Sin datos aún", ha="center", va="center",
                    fontsize=13, color="gray", transform=ax.transAxes)
            ax.set_title("Ingresos vs Gastos", fontsize=11)
        else:
            bars = ax.bar(["Ingresos", "Gastos"], [ti, tg],
                          color=["#4caf50", "#f44336"], width=0.5)
            max_val = max(ti, tg)
            for bar, val in zip(bars, [ti, tg]):
                ax.text(bar.get_x() + bar.get_width() / 2,
                        bar.get_height() + max_val * 0.02,
                        f"{val:.2f} €", ha="center", va="bottom",
                        fontsize=10, fontweight="bold")
            ax.set_title("Ingresos vs Gastos", fontsize=11)
            ax.set_ylabel("€")
            ax.grid(axis="y", alpha=0.3)
            ax.spines["top"].set_visible(False)
            ax.spines["right"].set_visible(False)

        self._fit_canvas(self.GastosIngresos_Graph, self._canvas_bar, self._fig_bar)

    def _update_resumen(self):
        s = self.fm.get_summary()
        lines = [f"<b>📅 {self.fm.current_month_label}</b><br>"]
        lines.append(f"💰 Ingresos: <b>{s['total_ingresos']:.2f} €</b>")
        lines.append(f"💸 Gastos:   <b>{s['total_gastos']:.2f} €</b>")
        color = "green" if s["balance"] >= 0 else "red"
        emoji = "✅" if s["balance"] >= 0 else "⚠️"
        lines.append(f"{emoji} Balance: <b style='color:{color}'>{s['balance']:.2f} €</b>")

        if s["ingresos_agg"]:
            lines.append("<br><u>Ingresos:</u>")
            for nombre, valor in s["ingresos_agg"].items():
                lines.append(f"&nbsp;&nbsp;· {nombre}: {valor:.2f} €")
        if s["gastos_agg"]:
            lines.append("<br><u>Gastos:</u>")
            ti = s["total_ingresos"]
            for nombre, valor in sorted(s["gastos_agg"].items(), key=lambda x: -x[1]):
                pct = (valor / ti * 100) if ti > 0 else 0
                lines.append(f"&nbsp;&nbsp;· {nombre}: {valor:.2f} € ({pct:.1f}%)")
        if s["ahorros_agg"]:
            lines.append("<br><u>Ahorros:</u>")
            for nombre, valor in s["ahorros_agg"].items():
                lines.append(f"&nbsp;&nbsp;· {nombre}: {valor:.2f} €")

        self.resumen_label.setHtml("<br>".join(lines))

    # ═══════════════════════════════════════════════════════════════════════
    # PESTAÑA 2 — Comparación
    # ═══════════════════════════════════════════════════════════════════════

    def _setup_tab_comparacion(self):
        self._fig_cat,  self._canvas_cat,  self._ax_cat  = self._make_canvas(self.comp_graph_categorias)
        self._fig_tend, self._canvas_tend, self._ax_tend = self._make_canvas(self.comp_graph_tendencia)
        self._populate_comp_combos()

    def _populate_comp_combos(self):
        months = self.fm.list_available_months()

        for combo in (self.comp_combo_a, self.comp_combo_b):
            combo.blockSignals(True)
            combo.clear()
            for dt, fname in months:
                combo.addItem(f"{MONTHS_ES[dt.month]} {dt.year}", userData=fname)
            combo.blockSignals(False)

        # Por defecto: A = penúltimo, B = último (o ambos al primero si solo hay uno)
        if len(months) >= 2:
            self.comp_combo_a.setCurrentIndex(len(months) - 2)
            self.comp_combo_b.setCurrentIndex(len(months) - 1)
        elif len(months) == 1:
            self.comp_combo_a.setCurrentIndex(0)
            self.comp_combo_b.setCurrentIndex(0)

        self._refresh_comparacion()

    def _refresh_comparacion(self):
        months = self.fm.list_available_months()
        if len(months) < 2:
            self.comp_resumen.setHtml(
                "<i>Necesitas al menos 2 meses con datos para comparar.</i>"
            )
            self._ax_cat.clear()
            self._canvas_cat.draw()
            self._ax_tend.clear()
            self._canvas_tend.draw()
            return
        idx_a = self.comp_combo_a.currentIndex()
        idx_b = self.comp_combo_b.currentIndex()
        fname_a = self.comp_combo_a.currentData()
        fname_b = self.comp_combo_b.currentData()

        if not fname_a or not fname_b or fname_a == fname_b:
            self.comp_resumen.setHtml("<i>Selecciona dos meses distintos.</i>")
            return

        r = self.fm.comparar_dos_meses(fname_a, fname_b)
        self._draw_comp_resumen(r)
        self._draw_comp_categorias(r)
        self._draw_comp_recurrentes()

    def _draw_comp_resumen(self, r: dict):
        lines = [f"<b>📊 {r['mes1']} vs {r['mes2']}</b><br>"]

        def _diff_str(val, ref):
            if ref == 0:
                return ""
            pct = (val / ref) * 100
            color = "red" if val > 0 else "green"
            arrow = "▲" if val > 0 else "▼"
            return f" <span style='color:{color}'>{arrow} {abs(val):.2f} € ({abs(pct):.1f}%)</span>"

        lines.append(f"<b>{r['mes1']}</b>")
        lines.append(f"&nbsp;&nbsp;Ingresos: {r['total_ingresos1']:.2f} €")
        lines.append(f"&nbsp;&nbsp;Gastos: {r['total_gastos1']:.2f} €")
        bal1 = r['total_ingresos1'] - r['total_gastos1']
        c1 = "green" if bal1 >= 0 else "red"
        lines.append(f"&nbsp;&nbsp;Balance: <span style='color:{c1}'>{bal1:.2f} €</span>")

        lines.append(f"<br><b>{r['mes2']}</b>")
        lines.append(f"&nbsp;&nbsp;Ingresos: {r['total_ingresos2']:.2f} €"
                     + _diff_str(r['diff_ingresos'], r['total_ingresos1']))
        lines.append(f"&nbsp;&nbsp;Gastos: {r['total_gastos2']:.2f} €"
                     + _diff_str(r['diff_gastos'], r['total_gastos1']))
        bal2 = r['total_ingresos2'] - r['total_gastos2']
        c2 = "green" if bal2 >= 0 else "red"
        lines.append(f"&nbsp;&nbsp;Balance: <span style='color:{c2}'>{bal2:.2f} €</span>")

        lines.append("<br><u>Por categoría:</u>")
        for d in sorted(r["detalle"], key=lambda x: -abs(x["diff"])):
            if d["estado"] == "NUEVO":
                lines.append(f"&nbsp;&nbsp;🆕 {d['cat']}: — → {d['val2']:.2f} €")
            elif d["estado"] == "ELIMINADO":
                lines.append(f"&nbsp;&nbsp;❌ {d['cat']}: {d['val1']:.2f} € → —")
            else:
                color = "red" if d["diff"] > 0 else "green" if d["diff"] < 0 else "gray"
                arrow = "▲" if d["diff"] > 0 else "▼" if d["diff"] < 0 else "="
                pct_str = f" ({d['pct_cambio']:+.1f}%)" if d["pct_cambio"] is not None else ""
                lines.append(
                    f"&nbsp;&nbsp;<span style='color:{color}'>{arrow}</span> "
                    f"{d['cat']}: {d['val1']:.2f} → {d['val2']:.2f} €{pct_str}"
                )

        self._draw_comp_resumen_recurrentes(lines)
        self.comp_resumen.setHtml("<br>".join(lines))

    def _draw_comp_resumen_recurrentes(self, lines: list):
        """Añade la sección de recurrentes al panel de texto."""
        r = self.fm.analizar_gastos_recurrentes()
        if r["total_meses"] < 2:
            return

        lines.append("<br><hr>")
        lines.append(f"<b>📌 Gastos recurrentes ({r['total_meses']} meses)</b>")

        if r["fijos"]:
            lines.append("<br><u>🔴 Fijos (todos los meses):</u>")
            for cat, aps in r["fijos"]:
                vals = [a["valor"] for a in aps]
                media = sum(vals) / len(vals)
                lines.append(
                    f"&nbsp;&nbsp;· {cat}: media <b>{media:.2f} €</b>"
                    f" (mín {min(vals):.2f} / máx {max(vals):.2f})"
                )

        if r["recurrentes"]:
            lines.append("<br><u>🟠 Recurrentes (50%+ meses):</u>")
            for cat, aps in r["recurrentes"]:
                vals = [a["valor"] for a in aps]
                media = sum(vals) / len(vals)
                lines.append(
                    f"&nbsp;&nbsp;· {cat}: media <b>{media:.2f} €</b>"
                    f" ({len(aps)}/{r['total_meses']} meses)"
                )

        if r["ocasionales"]:
            lines.append("<br><u>⚪ Ocasionales:</u>")
            for cat, aps in r["ocasionales"]:
                vals = [a["valor"] for a in aps]
                media = sum(vals) / len(vals)
                lines.append(
                    f"&nbsp;&nbsp;· {cat}: media <b>{media:.2f} €</b>"
                    f" ({len(aps)}/{r['total_meses']} meses)"
                )

    def _draw_comp_categorias(self, r: dict):
        ax = self._ax_cat
        ax.clear()

        cats    = r["categorias"]
        vals1   = r["valores1"]
        vals2   = r["valores2"]
        mes1    = r["mes1"]
        mes2    = r["mes2"]

        if not cats:
            ax.text(0.5, 0.5, "Sin categorías comunes", ha="center", va="center",
                    fontsize=12, color="gray", transform=ax.transAxes)
            self._fit_canvas(self.comp_graph_categorias, self._canvas_cat, self._fig_cat)
            return

        import numpy as np
        x = np.arange(len(cats))
        w = 0.35
        b1 = ax.bar(x - w / 2, vals1, w, label=mes1, color="#5b9bd5", alpha=0.85)
        b2 = ax.bar(x + w / 2, vals2, w, label=mes2, color="#ed7d31", alpha=0.85)
        ax.set_xticks(x)
        ax.set_xticklabels(cats, rotation=35, ha="right", fontsize=9)
        ax.set_ylabel("€")
        ax.set_title("Gastos por categoría", fontsize=11)
        ax.legend(fontsize=9)
        ax.grid(axis="y", alpha=0.3)
        ax.spines["top"].set_visible(False)
        ax.spines["right"].set_visible(False)

        self._fit_canvas(self.comp_graph_categorias, self._canvas_cat, self._fig_cat)

    def _draw_comp_recurrentes(self):
        ax = self._ax_tend
        ax.clear()

        r = self.fm.analizar_gastos_recurrentes()

        if r["total_meses"] < 2:
            ax.text(0.5, 0.5, "Se necesitan al menos 2 meses", ha="center", va="center",
                    fontsize=12, color="gray", transform=ax.transAxes)
            self._fit_canvas(self.comp_graph_tendencia, self._canvas_tend, self._fig_tend)
            return

        # Construir lista plana: (cat, media, tipo) ordenada por media desc
        filas = []
        for cat, aps in r["fijos"]:
            vals = [a["valor"] for a in aps]
            filas.append((cat, sum(vals) / len(vals), "fijo"))
        for cat, aps in r["recurrentes"]:
            vals = [a["valor"] for a in aps]
            filas.append((cat, sum(vals) / len(vals), "recurrente"))
        for cat, aps in r["ocasionales"]:
            vals = [a["valor"] for a in aps]
            filas.append((cat, sum(vals) / len(vals), "ocasional"))

        if not filas:
            ax.text(0.5, 0.5, "Sin categorías para analizar", ha="center", va="center",
                    fontsize=12, color="gray", transform=ax.transAxes)
            self._fit_canvas(self.comp_graph_tendencia, self._canvas_tend, self._fig_tend)
            return

        filas.sort(key=lambda x: x[1], reverse=True)

        cats   = [f[0] for f in filas]
        medias = [f[1] for f in filas]
        tipos  = [f[2] for f in filas]
        colores = {"fijo": "#e74c3c", "recurrente": "#e67e22", "ocasional": "#95a5a6"}
        colors  = [colores[t] for t in tipos]

        import numpy as np
        y = np.arange(len(cats))
        bars = ax.barh(y, medias, color=colors, alpha=0.85, height=0.6)

        # Etiqueta con valor a la derecha de cada barra
        for bar, val in zip(bars, medias):
            ax.text(bar.get_width() + max(medias) * 0.01, bar.get_y() + bar.get_height() / 2,
                    f"{val:.2f} €", va="center", fontsize=8)

        ax.set_yticks(y)
        ax.set_yticklabels(cats, fontsize=9)
        ax.invert_yaxis()
        ax.set_xlabel("Gasto medio mensual (€)")
        ax.set_title("Gastos recurrentes — media por categoría", fontsize=11)
        ax.grid(axis="x", alpha=0.3)
        ax.spines["top"].set_visible(False)
        ax.spines["right"].set_visible(False)

        # Leyenda de colores por tipo
        from matplotlib.patches import Patch
        legend_elements = [
            Patch(facecolor="#e74c3c", alpha=0.85, label=f"Fijo ({len(r['fijos'])} cat.)"),
            Patch(facecolor="#e67e22", alpha=0.85, label=f"Recurrente ({len(r['recurrentes'])} cat.)"),
            Patch(facecolor="#95a5a6", alpha=0.85, label=f"Ocasional ({len(r['ocasionales'])} cat.)"),
        ]
        ax.legend(handles=legend_elements, fontsize=9, loc="lower right")

        self._fit_canvas(self.comp_graph_tendencia, self._canvas_tend, self._fig_tend)

    # ═══════════════════════════════════════════════════════════════════════
    # Señales
    # ═══════════════════════════════════════════════════════════════════════

    def _connect_signals(self):
        # Pestaña 1
        self.pushButton.clicked.connect(self._on_ok_clicked)
        self.mes_combo.currentIndexChanged.connect(self._on_month_changed)
        # Pestaña 2
        self.comp_combo_a.currentIndexChanged.connect(self._refresh_comparacion)
        self.comp_combo_b.currentIndexChanged.connect(self._refresh_comparacion)
        # Refrescar comparación al cambiar de pestaña (por si se añadieron datos)
        self.tabWidget.currentChanged.connect(self._on_tab_changed)

    def _on_tab_changed(self, index: int):
        if index == 1:
            self._populate_comp_combos()

    # ═══════════════════════════════════════════════════════════════════════
    # Helpers compartidos
    # ═══════════════════════════════════════════════════════════════════════

    def _update_title(self):
        self.setWindowTitle(f"Chiclin — {self.fm.current_month_label}")

    @staticmethod
    def _make_canvas(graphics_view) -> tuple:
        fig = Figure(tight_layout=True)
        canvas = FigureCanvas(fig)
        ax = fig.add_subplot(111)
        scene = QtWidgets.QGraphicsScene()
        scene.addWidget(canvas)
        graphics_view.setScene(scene)
        graphics_view.setHorizontalScrollBarPolicy(QtCore.Qt.ScrollBarAlwaysOff)
        graphics_view.setVerticalScrollBarPolicy(QtCore.Qt.ScrollBarAlwaysOff)
        return fig, canvas, ax

    def _fit_canvas(self, graphics_view, canvas, fig):
        w, h = graphics_view.width(), graphics_view.height()
        canvas.setFixedSize(w, h)
        graphics_view.scene().setSceneRect(0, 0, w, h)
        fig.tight_layout()
        canvas.draw()

    def resizeEvent(self, event):
        super().resizeEvent(event)
        QtCore.QTimer.singleShot(50, self.refresh_mes)
        if self.tabWidget.currentIndex() == 1:
            QtCore.QTimer.singleShot(50, self._refresh_comparacion)