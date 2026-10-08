"""Mapeamento das classes de cada dataset de origem para as 7 classes do ReciclaTHE.

A ORDEM de CLASSES é contratual: é a mesma usada pela API e pelo app.
Classes de origem mapeadas para None são DESCARTADAS (ambíguas para o
contexto de descarte em Teresina). Justificativas em ml/DATASETS.md.
"""

CLASSES = ["PLASTICO", "PAPEL", "METAL", "VIDRO", "ELETRONICO", "ORGANICO", "REJEITO"]

# TrashNet (pastas em minúsculas)
TRASHNET = {
    "cardboard": "PAPEL",
    "paper": "PAPEL",
    "glass": "VIDRO",
    "metal": "METAL",
    "plastic": "PLASTICO",
    "trash": "REJEITO",
}

# RealWaste (nomes das pastas)
REALWASTE = {
    "Cardboard": "PAPEL",
    "Paper": "PAPEL",
    "Glass": "VIDRO",
    "Metal": "METAL",
    "Plastic": "PLASTICO",
    "Food Organics": "ORGANICO",
    "Vegetation": "ORGANICO",
    "Miscellaneous Trash": "REJEITO",
    # Têxteis não entram na coleta seletiva convencional de Teresina e não
    # há ponto de entrega específico mapeado; tratados como rejeito
    # (orientação do app: doação quando em bom estado). Ver DATASETS.md.
    "Textile Trash": "REJEITO",
}

# TACO: chave = nome da categoria (campo "name" do COCO)
TACO = {
    "Aluminium foil": "METAL",
    "Battery": "ELETRONICO",
    "Clear plastic bottle": "PLASTICO",
    "Other plastic bottle": "PLASTICO",
    "Glass bottle": "VIDRO",
    "Metal bottle cap": "METAL",
    "Plastic bottle cap": "PLASTICO",
    "Broken glass": "VIDRO",
    "Drink can": "METAL",
    "Food Can": "METAL",
    "Corrugated carton": "PAPEL",
    "Egg carton": "PAPEL",
    "Meal carton": "PAPEL",
    "Other carton": "PAPEL",
    "Pizza box": "PAPEL",
    "Toilet tube": "PAPEL",
    "Cigarette": "REJEITO",
    "Disposable plastic cup": "PLASTICO",
    "Other plastic cup": "PLASTICO",
    "Glass cup": "VIDRO",
    "Food waste": "ORGANICO",
    "Glass jar": "VIDRO",
    "Metal lid": "METAL",
    "Plastic lid": "PLASTICO",
    "Other plastic": "PLASTICO",
    "Magazine paper": "PAPEL",
    "Normal paper": "PAPEL",
    "Wrapping paper": "PAPEL",
    "Tissues": "REJEITO",
    "Paper bag": "PAPEL",
    "Crisp packet": "REJEITO",          # embalagem metalizada (BOPP), em geral não reciclada
    "Garbage bag": "PLASTICO",
    "Other plastic wrapper": "PLASTICO",
    "Plastic film": "PLASTICO",
    "Polypropylene bag": "PLASTICO",
    "Single-use carrier bag": "PLASTICO",
    "Six pack rings": "PLASTICO",
    "Disposable food container": "PLASTICO",
    "Other plastic container": "PLASTICO",
    "Spread tub": "PLASTICO",
    "Tupperware": "PLASTICO",
    "Plastic glooves": "REJEITO",
    "Plastic utensils": "PLASTICO",
    "Pop tab": "METAL",
    "Rope & strings": "REJEITO",
    "Scrap metal": "METAL",
    "Shoe": "REJEITO",
    "Plastic straw": "PLASTICO",
    # Descartadas por ambiguidade de destino ou de rótulo:
    "Aerosol": None,                     # perigoso/pressurizado, destino específico
    "Aluminium blister pack": None,      # multicamada
    "Carded blister pack": None,
    "Drink carton": None,                # longa vida (multicamada)
    "Paper cup": None,                   # papel com filme plástico
    "Foam cup": None,                    # isopor: aceitação varia
    "Foam food container": None,
    "Styrofoam piece": None,
    "Squeezable tube": None,
    "Paper straw": None,
    "Unlabeled litter": None,            # sem rótulo de material
}

# GIZ e-waste (Gana): todas as categorias são equipamentos eletroeletrônicos
GIZ = {
    "Computers": "ELETRONICO",
    "ACs": "ELETRONICO",
    "Laptops": "ELETRONICO",
    "TV": "ELETRONICO",
    "Compressors": "ELETRONICO",
    "Microwave": "ELETRONICO",
    "Fridges": "ELETRONICO",
    "unsure": None,
}
