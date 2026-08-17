# -*- coding: utf-8 -*-
"""
logic.py — Lógica de negocio de Chiclin.

Contiene la clase FinanceManager con toda la gestión de datos,
CSV y cálculos. Sin prints, sin plt.show(): la UI y el CLI
consumen esta clase y deciden cómo presentar los resultados.
"""

import csv
import os
import sys
from datetime import datetime
from collections import defaultdict


# ======================
# CONSTANTES
# ======================

MONTHS_ES = {
    1: "Enero", 2: "Febrero", 3: "Marzo", 4: "Abril",
    5: "Mayo", 6: "Junio", 7: "Julio", 8: "Agosto",
    9: "Septiembre", 10: "Octubre", 11: "Noviembre", 12: "Diciembre",
}

TIPOS = ("gastos", "ingresos", "ahorros")


def get_base_path() -> str:
    # sys.argv[0] apunta al .exe real tanto en PyInstaller como en Nuitka onefile,
    # y al .py en ejecución normal — siempre es la ubicación correcta.
    return os.path.dirname(os.path.abspath(sys.argv[0]))


# ======================
# FINANCE MANAGER
# ======================

class FinanceManager:
    """
    Gestiona los datos financieros de un mes:
    gastos, ingresos y ahorros.

    Cada entrada se almacena con clave «nombre_N» para permitir
    múltiples registros del mismo concepto en el mismo mes.
    """

    def __init__(self):
        self.data_dir = os.path.join(get_base_path(), "chiclin")
        os.makedirs(self.data_dir, exist_ok=True)

        self.gastos: dict[str, float] = {}
        self.ingresos: dict[str, float] = {}
        self.ahorros: dict[str, float] = {}

        self.current_filename: str | None = None
        self.current_filepath: str | None = None

    # --------------------------------------------------
    # Propiedades de conveniencia
    # --------------------------------------------------

    @property
    def current_month_label(self) -> str:
        """Devuelve «Marzo 2025» a partir del filename activo."""
        if not self.current_filename:
            return ""
        month, year = self.current_filename.replace(".csv", "").split("_")
        return f"{MONTHS_ES[int(month)]} {year}"

    # --------------------------------------------------
    # Gestión de meses
    # --------------------------------------------------

    def get_month_filename(self) -> str:
        """
        Devuelve el nombre de fichero del mes actual.
        Busca primero si ya existe en disco (con o sin zero-padding)
        para ser compatible con ficheros creados por versiones anteriores.
        """
        ahora = datetime.now()
        # Candidatos: con y sin zero-padding
        candidates = [
            f"{ahora.month:02d}_{ahora.year}.csv",
            f"{ahora.month}_{ahora.year}.csv",
        ]
        for c in candidates:
            if os.path.exists(os.path.join(self.data_dir, c)):
                return c
        # Si no existe ninguno, usar el formato con zero-padding (estándar)
        return candidates[0]

    def list_available_months(self, exclude_current: bool = False) -> list[tuple[datetime, str]]:
        """
        Devuelve una lista ordenada de (datetime, filename)
        para todos los CSV existentes en data_dir.

        Si exclude_current=True, excluye el mes en curso
        (útil para el menú «Mes anterior» del CLI).
        """
        current = self.get_month_filename()
        months = []
        for f in os.listdir(self.data_dir):
            if not f.endswith(".csv"):
                continue
            if exclude_current and f == current:
                continue
            try:
                month, year = f.replace(".csv", "").split("_")
                dt = datetime(int(year), int(month), 1)
                months.append((dt, f))
            except ValueError:
                continue
        months.sort(key=lambda x: x[0])
        return months

    def load_month(self, filename: str) -> None:
        """Carga un mes en memoria (sobreescribe el estado actual)."""
        self.current_filename = filename
        self.current_filepath = os.path.join(self.data_dir, filename)
        self._clear_data()
        self._load_from_file(self.current_filepath)

    def load_current_month(self) -> None:
        """Atajo para cargar el mes en curso."""
        self.load_month(self.get_month_filename())

    def switch_month(self, filename: str) -> None:
        """
        Guarda el mes activo y carga el nuevo.
        No hace nada si es el mismo fichero.
        """
        if filename == self.current_filename:
            return
        if self.current_filepath:
            self.save()
        self.load_month(filename)

    # --------------------------------------------------
    # CRUD
    # --------------------------------------------------

    def add_entry(self, nombre: str, valor: float, tipo: str) -> None:
        """
        Añade una entrada al mes activo y guarda el CSV.
        tipo debe ser 'gastos', 'ingresos' o 'ahorros'.
        """
        if tipo not in TIPOS:
            raise ValueError(f"tipo debe ser uno de {TIPOS}")
        if valor <= 0:
            raise ValueError("El valor debe ser mayor que 0.")

        target = self._target(tipo)
        count = sum(1 for k in target if k.startswith(f"{nombre}_"))
        target[f"{nombre}_{count}"] = valor
        self.save()

    def delete_entry(self, key: str, tipo: str) -> None:
        """Elimina una entrada por su clave exacta y guarda."""
        target = self._target(tipo)
        if key in target:
            del target[key]
            self.save()

    # --------------------------------------------------
    # CSV
    # --------------------------------------------------

    def save(self) -> None:
        """Escribe el estado actual en el CSV activo."""
        if not self.current_filepath:
            return
        with open(self.current_filepath, "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["tipo", "nombre", "valor"])
            for d, t in [
                (self.ingresos, "ingresos"),
                (self.gastos, "gastos"),
                (self.ahorros, "ahorros"),
            ]:
                for k, v in d.items():
                    writer.writerow([t, k, v])

    # --------------------------------------------------
    # Estadísticas — mes activo
    # --------------------------------------------------

    def get_summary(self) -> dict:
        """
        Devuelve un dict con los totales y los dicts agregados
        del mes activo. Seguro para consumir desde UI o CLI.

        Estructura devuelta:
        {
            "total_ingresos": float,
            "total_gastos":   float,
            "total_ahorros":  float,
            "balance":        float,
            "gastos_agg":     {nombre: float, ...},
            "ingresos_agg":   {nombre: float, ...},
            "ahorros_agg":    {nombre: float, ...},
        }
        """
        gastos_agg   = self._aggregate(self.gastos)
        ingresos_agg = self._aggregate(self.ingresos)
        ahorros_agg  = self._aggregate(self.ahorros)

        total_ingresos = sum(ingresos_agg.values())
        total_gastos   = sum(gastos_agg.values())
        total_ahorros  = sum(ahorros_agg.values())

        return {
            "total_ingresos": total_ingresos,
            "total_gastos":   total_gastos,
            "total_ahorros":  total_ahorros,
            "balance":        total_ingresos - total_gastos,
            "gastos_agg":     gastos_agg,
            "ingresos_agg":   ingresos_agg,
            "ahorros_agg":    ahorros_agg,
        }

    def get_pie_data(self) -> dict:
        """
        Datos listos para dibujar el gráfico de tarta de gastos.

        Devuelve:
        {
            "valores":   [float, ...],   ← gastos + (resto si ingresos > gastos)
            "etiquetas": [str, ...],
        }
        """
        s = self.get_summary()
        gastos_agg = s["gastos_agg"]

        valores   = list(gastos_agg.values())
        etiquetas = list(gastos_agg.keys())
        resto = s["total_ingresos"] - s["total_gastos"]
        if resto > 0:
            valores.append(resto)
            etiquetas.append("Resto del ingreso")

        return {"valores": valores, "etiquetas": etiquetas}

    def get_bar_data(self) -> dict:
        """
        Datos para el gráfico de barras Ingresos vs Gastos.

        Devuelve:
        {
            "total_ingresos": float,
            "total_gastos":   float,
        }
        """
        s = self.get_summary()
        return {
            "total_ingresos": s["total_ingresos"],
            "total_gastos":   s["total_gastos"],
        }

    # --------------------------------------------------
    # Estadísticas — comparación entre meses
    # --------------------------------------------------

    def load_month_data(self, filename: str) -> dict:
        """
        Carga un mes concreto sin modificar el estado activo.

        Devuelve:
        {
            "gastos":   {key: float},
            "ingresos": {key: float},
            "ahorros":  {key: float},
        }
        """
        filepath = os.path.join(self.data_dir, filename)
        gastos, ingresos, ahorros = {}, {}, {}
        if not os.path.exists(filepath):
            return {"gastos": gastos, "ingresos": ingresos, "ahorros": ahorros}

        with open(filepath, newline="", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                tipo   = row["tipo"]
                nombre = row["nombre"]
                valor  = float(row["valor"])
                if tipo == "ingresos":
                    ingresos[nombre] = valor
                elif tipo == "gastos":
                    gastos[nombre] = valor
                else:
                    ahorros[nombre] = valor

        return {"gastos": gastos, "ingresos": ingresos, "ahorros": ahorros}

    def comparar_dos_meses(self, filename1: str, filename2: str) -> dict:
        """
        Compara dos meses y devuelve todos los datos necesarios
        para que el presentador (CLI o UI) los muestre.

        Devuelve:
        {
            "mes1": str,
            "mes2": str,
            "total_gastos1":   float,
            "total_gastos2":   float,
            "total_ingresos1": float,
            "total_ingresos2": float,
            "diff_gastos":     float,
            "diff_ingresos":   float,
            "categorias":      [str, ...],         ← unión ordenada
            "valores1":        [float, ...],
            "valores2":        [float, ...],
            "detalle":         [{cat, val1, val2, diff, pct_cambio, estado}]
        }
        """
        def _parse_label(fname):
            m, y = fname.replace(".csv", "").split("_")
            return f"{MONTHS_ES[int(m)]} {y}"

        d1 = self.load_month_data(filename1)
        d2 = self.load_month_data(filename2)

        g1 = self._aggregate(d1["gastos"])
        g2 = self._aggregate(d2["gastos"])
        i1 = self._aggregate(d1["ingresos"])
        i2 = self._aggregate(d2["ingresos"])

        tg1, tg2 = sum(g1.values()), sum(g2.values())
        ti1, ti2 = sum(i1.values()), sum(i2.values())

        todas = sorted(set(g1) | set(g2))
        detalle = []
        for cat in todas:
            v1, v2 = g1.get(cat, 0), g2.get(cat, 0)
            diff = v2 - v1
            if v1 == 0:
                estado, pct = "NUEVO", None
            elif v2 == 0:
                estado, pct = "ELIMINADO", None
            else:
                pct = (diff / v1) * 100
                estado = "↑" if diff > 0 else "↓" if diff < 0 else "="
            detalle.append({
                "cat": cat, "val1": v1, "val2": v2,
                "diff": diff, "pct_cambio": pct, "estado": estado,
            })

        return {
            "mes1": _parse_label(filename1),
            "mes2": _parse_label(filename2),
            "total_gastos1":   tg1,
            "total_gastos2":   tg2,
            "total_ingresos1": ti1,
            "total_ingresos2": ti2,
            "diff_gastos":     tg2 - tg1,
            "diff_ingresos":   ti2 - ti1,
            "categorias":      todas,
            "valores1":        [g1.get(c, 0) for c in todas],
            "valores2":        [g2.get(c, 0) for c in todas],
            "detalle":         detalle,
        }

    def comparar_todos_meses(self) -> list[dict]:
        """
        Tendencia de todos los meses disponibles.

        Devuelve lista ordenada de:
        {
            "fecha":          datetime,
            "nombre":         str,
            "total_gastos":   float,
            "total_ingresos": float,
            "balance":        float,
            "gastos_agg":     {cat: float},
        }
        """
        meses = self.list_available_months()
        resultado = []
        for dt, filename in meses:
            d = self.load_month_data(filename)
            g = self._aggregate(d["gastos"])
            i = self._aggregate(d["ingresos"])
            tg, ti = sum(g.values()), sum(i.values())
            resultado.append({
                "fecha":          dt,
                "nombre":         f"{MONTHS_ES[dt.month]} {dt.year}",
                "total_gastos":   tg,
                "total_ingresos": ti,
                "balance":        ti - tg,
                "gastos_agg":     g,
            })
        return resultado

    def analizar_gastos_recurrentes(self) -> dict:
        """
        Clasifica categorías de gasto en fijas, recurrentes y ocasionales.

        Devuelve:
        {
            "fijos":       [(cat, [aparicion, ...]), ...],
            "recurrentes": [(cat, [aparicion, ...]), ...],
            "ocasionales": [(cat, [aparicion, ...]), ...],
            "total_meses": int,
        }
        donde cada «aparicion» es {"mes": str, "valor": float, "fecha": datetime}.
        """
        meses = self.list_available_months()
        if len(meses) < 2:
            return {"fijos": [], "recurrentes": [], "ocasionales": [], "total_meses": len(meses)}

        apariciones: dict[str, list] = defaultdict(list)
        for dt, filename in meses:
            d = self.load_month_data(filename)
            g = self._aggregate(d["gastos"])
            mes_nombre = f"{MONTHS_ES[dt.month]} {dt.year}"
            for cat, valor in g.items():
                apariciones[cat].append({"mes": mes_nombre, "valor": valor, "fecha": dt})

        total_meses = len(meses)
        fijos, recurrentes, ocasionales = [], [], []

        for cat, aps in apariciones.items():
            n = len(aps)
            if n == total_meses:
                fijos.append((cat, aps))
            elif n >= total_meses / 2:
                recurrentes.append((cat, aps))
            else:
                ocasionales.append((cat, aps))

        return {
            "fijos":       sorted(fijos),
            "recurrentes": sorted(recurrentes),
            "ocasionales": sorted(ocasionales),
            "total_meses": total_meses,
        }

    # --------------------------------------------------
    # Helpers internos
    # --------------------------------------------------

    def _target(self, tipo: str) -> dict:
        return {"gastos": self.gastos, "ingresos": self.ingresos, "ahorros": self.ahorros}[tipo]

    def _clear_data(self) -> None:
        self.gastos.clear()
        self.ingresos.clear()
        self.ahorros.clear()

    def _load_from_file(self, path: str) -> None:
        if not os.path.exists(path):
            return
        with open(path, newline="", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                tipo   = row["tipo"]
                nombre = row["nombre"]
                valor  = float(row["valor"])
                self._target(tipo)[nombre] = valor

    @staticmethod
    def _aggregate(data: dict) -> dict:
        """Suma entradas con el mismo nombre base (sin el sufijo _N)."""
        aggregated: dict[str, float] = {}
        for key, value in data.items():
            base = key.rsplit("_", 1)[0]
            aggregated[base] = aggregated.get(base, 0) + value
        return aggregated

    @staticmethod
    def autopct_if_big_enough(pct: float) -> str:
        return f"{pct:.1f}%" if pct >= 2 else ""