#!/usr/bin/env python3
import os
import sys
import re
import json
import email
from email import policy
import subprocess
from html.parser import HTMLParser

BASE_DIR = "/storage/emulated/0/a pin out 2"
DEST_DIR = "/data/data/com.termux/files/home/pin_out_2_project/data"
EXTRACTED_DIR = os.path.join(DEST_DIR, "extracted")
os.makedirs(EXTRACTED_DIR, exist_ok=True)

class HTMLTextExtractor(HTMLParser):
    def __init__(self):
        super().__init__()
        self.text_parts = []
    def handle_data(self, data):
        txt = data.strip()
        if txt:
            self.text_parts.append(txt)
    def get_text(self):
        return " ".join(self.text_parts)

def clean_text_from_html(html_content):
    parser = HTMLTextExtractor()
    try:
        parser.feed(html_content)
        return parser.get_text()
    except Exception:
        return re.sub(r"<[^>]+>", " ", html_content)

def parse_hhc(hhc_path):
    if not os.path.exists(hhc_path):
        return []
    with open(hhc_path, "r", encoding="latin1", errors="ignore") as f:
        content = f.read()

    items = []
    current_category = ""
    objects = re.findall(r"<OBJECT[^>]*>(.*?)</OBJECT>", content, re.DOTALL | re.IGNORECASE)
    
    for obj in objects:
        name_match = re.search(r'name=["\']Name["\']\s+value=["\']([^"\']+)["\']', obj, re.IGNORECASE)
        local_match = re.search(r'name=["\']Local["\']\s+value=["\']([^"\']+)["\']', obj, re.IGNORECASE)
        name = name_match.group(1).strip() if name_match else ""
        local = local_match.group(1).strip() if local_match else ""
        if name and not local:
            current_category = name
        elif name and local:
            items.append({"name": name, "local": local, "category": current_category})
    return items

def extract_chm(chm_path, out_dir):
    print(f"Extracting CHM: {os.path.basename(chm_path)} -> {out_dir}")
    os.makedirs(out_dir, exist_ok=True)
    res = subprocess.run(["7z", "x", "-y", f"-o{out_dir}", chm_path], capture_output=True, text=True)
    if res.returncode != 0:
        print(f"Error extracting {chm_path}: {res.stderr}")
    else:
        print(f"Successfully extracted {os.path.basename(chm_path)}")

def extract_mht_file(mht_path, out_dir, rel_prefix):
    os.makedirs(out_dir, exist_ok=True)
    try:
        with open(mht_path, "rb") as f:
            msg = email.message_from_binary_file(f, policy=policy.default)
        
        subject = msg["subject"] or os.path.splitext(os.path.basename(mht_path))[0]
        html_content = ""
        images = []
        img_idx = 0
        
        for part in msg.walk():
            ctype = part.get_content_type()
            cloc = part["Content-Location"] or part.get_filename() or ""
            data = part.get_payload(decode=True)
            if not data:
                continue
            
            if ctype.startswith("image/"):
                img_name = os.path.basename(cloc) if cloc else f"img_{img_idx}.jpg"
                img_name = re.sub(r"[^a-zA-Z0-9_.-]", "_", img_name)
                img_idx += 1
                img_save_path = os.path.join(out_dir, img_name)
                with open(img_save_path, "wb") as img_f:
                    img_f.write(data)
                images.append(os.path.join(rel_prefix, img_name))
            elif ctype == "text/html":
                try:
                    html_content = data.decode("utf-8")
                except Exception:
                    html_content = data.decode("latin1", errors="ignore")
        
        if html_content:
            for img in images:
                base_img = os.path.basename(img)
                html_content = re.sub(r"src=[\"\'][^\"\']*" + re.escape(base_img) + r"[\"\']", f"src=\"{base_img}\"", html_content, flags=re.IGNORECASE)
            
            with open(os.path.join(out_dir, "index.html"), "w", encoding="utf-8") as hf:
                hf.write(html_content)
        
        plain_text = clean_text_from_html(html_content)
        return {"subject": subject, "images": images, "plain_text": plain_text, "has_html": bool(html_content)}
    except Exception as e:
        print(f"Failed to process MHT {mht_path}: {e}")
        return None

def main():
    print("=== STARTING PIN OUT 2 EXTRACTION & INDEXING ===")
    chm_sources = [
        ("BSL BOOTMODE TRICORE PCM FLASH KTM PINOUT.chm", "bsl_bootmode_tricore", "BSL Bootmode Tricore / PCM Flash / KTM"),
        ("Pinout DM BOSCH SIEMENS CONTI Magneti Marelli v2.2.chm", "dm_bosch_siemens_marelli", "DM Pinout Bosch / Siemens / Conti / Marelli"),
        ("PCMKTM FLASH BENCHMODE (NO OPENING ECU) MODULE  71 Pinout.chm", "pcmktm_bench_module71", "PCMKTM Bench Mode (Module 71 - No Opening)"),
        ("Ecu Connections By HexPortal.com 11-5-21.chm", "hexportal_ecu_connections", "HexPortal ECU Connections")
    ]
    
    database = []
    item_id = 1
    
    for chm_filename, folder_name, category_name in chm_sources:
        chm_path = os.path.join(BASE_DIR, chm_filename)
        if not os.path.exists(chm_path):
            print(f"Warning: CHM file {chm_path} not found!")
            continue
        
        out_sub_dir = os.path.join(EXTRACTED_DIR, folder_name)
        extract_chm(chm_path, out_sub_dir)
        
        hhc_files = [f for f in os.listdir(out_sub_dir) if f.lower().endswith(".hhc")]
        toc_map = {}
        if hhc_files:
            toc_items = parse_hhc(os.path.join(out_sub_dir, hhc_files[0]))
            for item in toc_items:
                loc_clean = item["local"].replace("\\", "/").split("#")[0]
                toc_map[loc_clean.lower()] = item
        
        for root, dirs, files in os.walk(out_sub_dir):
            for f in sorted(files):
                if f.lower().endswith((".htm", ".html")):
                    full_htm_path = os.path.join(root, f)
                    rel_htm_path = os.path.relpath(full_htm_path, DEST_DIR)
                    rel_file_key = os.path.relpath(full_htm_path, out_sub_dir).replace("\\", "/").lower()
                    
                    with open(full_htm_path, "r", encoding="latin1", errors="ignore") as hf:
                        htm_raw = hf.read()
                    
                    title_match = re.search(r"<title>(.*?)</title>", htm_raw, re.IGNORECASE | re.DOTALL)
                    h1_match = re.search(r"<h1[^>]*>(.*?)</h1>", htm_raw, re.IGNORECASE | re.DOTALL)
                    
                    title = ""
                    if rel_file_key in toc_map:
                        title = toc_map[rel_file_key]["name"]
                    elif title_match:
                        title = clean_text_from_html(title_match.group(1)).strip()
                    elif h1_match:
                        title = clean_text_from_html(h1_match.group(1)).strip()
                    
                    if not title or title.lower() in ["index", "default", "table of contents", "untitled"]:
                        title = os.path.splitext(f)[0].replace("_", " ").strip()
                    
                    plain_text = clean_text_from_html(htm_raw)
                    
                    img_matches = re.findall(r"<img[^>]+src=[\"\']([^\"\']+)[\"\']", htm_raw, re.IGNORECASE)
                    images = []
                    for img_ref in img_matches:
                        img_ref_clean = img_ref.split("?")[0].split("#")[0]
                        img_full = os.path.normpath(os.path.join(root, img_ref_clean))
                        if os.path.exists(img_full):
                            images.append(os.path.relpath(img_full, DEST_DIR))
                    
                    if not images:
                        base_no_ext = os.path.splitext(f)[0]
                        for other_file in os.listdir(root):
                            if other_file.lower().endswith((".png", ".jpg", ".jpeg", ".gif", ".bmp")):
                                if base_no_ext.lower() in other_file.lower() or "drex_" + base_no_ext.lower() in other_file.lower():
                                    img_full = os.path.join(root, other_file)
                                    images.append(os.path.relpath(img_full, DEST_DIR))
                    
                    detected_brands = []
                    for b in ["Bosch", "Siemens", "Continental", "Magneti Marelli", "Marelli", "Delphi", "Denso", "ACDelco", "Transtron", "Visteon", "Lucas", "Hitachi", "TRW", "Sagem", "Valeo", "Motorola", "Ford", "BMW", "Mercedes", "Audi", "Volkswagen", "PSA", "Peugeot", "Citroen", "Renault", "Fiat", "Toyota", "Nissan", "Honda", "Hyundai", "Kia", "Volvo", "Opel", "Chevrolet", "Jeep", "Chrysler", "Dodge", "Land Rover", "Jaguar", "Porsche", "Suzuki", "Mazda", "Mitsubishi", "Subaru", "Isuzu"]:
                        if re.search(r"\b" + re.escape(b) + r"\b", title + " " + plain_text, re.IGNORECASE):
                            detected_brands.append(b)
                    
                    detected_mcus = []
                    for m in ["TC1724", "TC1728", "TC1766", "TC1767", "TC1782", "TC1793", "TC1796", "TC1797", "TC275", "TC297", "TC298", "TC299", "ST10F275", "ST10F280", "ST10", "MPC555", "MPC5566", "MPC5674", "MPC55xx", "MPC56xx", "SH7055", "SH7058", "SH7059", "SH72513", "SH72543", "SH72544", "M32R", "MH7203", "MH8206", "MH8305", "HC12", "HCS12", "TriCore", "Tricore", "BDM", "JTAG", "GPT"]:
                        if re.search(r"\b" + re.escape(m) + r"\b", title + " " + plain_text, re.IGNORECASE):
                            detected_mcus.append(m)
                    
                    database.append({
                        "id": item_id,
                        "title": title,
                        "source": folder_name,
                        "category": category_name,
                        "html_path": rel_htm_path,
                        "images": images,
                        "brands": list(set(detected_brands)),
                        "mcus": list(set(detected_mcus)),
                        "search_text": f"{title} {category_name} {' '.join(detected_brands)} {' '.join(detected_mcus)} {plain_text[:1200]}".lower(),
                        "summary": plain_text[:300].strip()
                    })
                    item_id += 1

    ktag_help_dir = os.path.join(BASE_DIR, "KTAG INSTRUCTION/help")
    if os.path.exists(ktag_help_dir):
        print(f"Extracting KTAG MHT files from {ktag_help_dir}...")
        ktag_out_dir = os.path.join(EXTRACTED_DIR, "ktag_instruction")
        mht_files = sorted([f for f in os.listdir(ktag_help_dir) if f.lower().endswith(".mht")])
        print(f"Found {len(mht_files)} KTAG MHT files.")
        
        def lang_priority(filename):
            if filename.startswith("ENU_"): return 0
            if filename.startswith("FRA_"): return 1
            if filename.startswith("ESN_"): return 2
            if filename.startswith("ITA_"): return 3
            if filename.startswith("DEU_"): return 4
            return 5
        
        mht_files_sorted = sorted(mht_files, key=lang_priority)
        
        for idx, mht_name in enumerate(mht_files_sorted):
            if (idx + 1) % 200 == 0 or idx == 0:
                print(f"Processing MHT {idx + 1}/{len(mht_files)}: {mht_name}")
            
            base_name = os.path.splitext(mht_name)[0]
            mht_file_path = os.path.join(ktag_help_dir, mht_name)
            sub_folder = os.path.join(ktag_out_dir, base_name)
            rel_prefix = os.path.join("extracted/ktag_instruction", base_name)
            
            res = extract_mht_file(mht_file_path, sub_folder, rel_prefix)
            if not res:
                continue
            
            title = res["subject"]
            if not title or title.strip() == base_name or len(title.strip()) < 3:
                lines = [l.strip() for l in res["plain_text"].splitlines() if l.strip()]
                title = f"{base_name} - {lines[0]}" if lines else base_name
            else:
                title = f"{base_name} - {title}"
            
            plain_text = res["plain_text"]
            
            detected_brands = []
            for b in ["Bosch", "Siemens", "Continental", "Magneti Marelli", "Marelli", "Delphi", "Denso", "ACDelco", "Transtron", "Visteon", "Lucas", "Hitachi", "TRW", "Sagem", "Valeo", "Motorola", "Ford", "BMW", "Mercedes", "Audi", "Volkswagen", "VAG", "PSA", "Peugeot", "Citroen", "Renault", "Fiat", "Toyota", "Nissan", "Honda", "Hyundai", "Kia", "Volvo", "Opel", "Chevrolet", "Jeep", "Chrysler", "Dodge", "Land Rover", "Jaguar", "Porsche", "Suzuki", "Mazda", "Mitsubishi", "Subaru", "Isuzu", "Iveco", "MAN", "Scania", "DAF", "Volvo Truck", "Cummins"]:
                if re.search(r"\b" + re.escape(b) + r"\b", title + " " + plain_text, re.IGNORECASE):
                    detected_brands.append(b)
            
            detected_mcus = []
            for m in ["TC1724", "TC1728", "TC1766", "TC1767", "TC1782", "TC1793", "TC1796", "TC1797", "TC275", "TC297", "ST10F275", "ST10F280", "ST10", "MPC555", "MPC5566", "MPC5674", "MPC55xx", "MPC56xx", "SH7055", "SH7058", "SH7059", "SH72513", "SH72543", "SH72544", "M32R", "MH7203", "MH8206", "MH8305", "HC12", "HCS12", "TriCore", "Tricore", "BDM", "JTAG", "GPT", "Nexus", "NBD", "Bootloader", "C167"]:
                if re.search(r"\b" + re.escape(m) + r"\b", title + " " + plain_text, re.IGNORECASE):
                    detected_mcus.append(m)
            
            lang = "English" if base_name.startswith("ENU_") else ("German" if base_name.startswith("DEU_") else ("French" if base_name.startswith("FRA_") else "Other"))
            
            database.append({
                "id": item_id,
                "title": title,
                "source": "ktag_instruction",
                "category": f"KTAG Instruction ({lang})",
                "lang": lang,
                "html_path": os.path.join(rel_prefix, "index.html"),
                "images": res["images"],
                "brands": list(set(detected_brands)),
                "mcus": list(set(detected_mcus)),
                "search_text": f"{title} KTAG {' '.join(detected_brands)} {' '.join(detected_mcus)} {plain_text[:1200]}".lower(),
                "summary": plain_text[:300].strip()
            })
            item_id += 1

    db_file = os.path.join(DEST_DIR, "pinout_master_database.json")
    print(f"\nWriting database with {len(database)} total pinout records to {db_file}...")
    with open(db_file, "w", encoding="utf-8") as db_f:
        json.dump(database, db_f, indent=2, ensure_ascii=False)
    
    app_index = []
    for item in database:
        app_index.append({
            "id": item["id"],
            "title": item["title"],
            "category": item["category"],
            "source": item["source"],
            "brands": item["brands"],
            "mcus": item["mcus"],
            "images_count": len(item["images"]),
            "preview_img": item["images"][0] if item["images"] else "",
            "html": item["html_path"],
            "summary": item["summary"][:160],
            "tags": f"{item['title']} {item['category']} {' '.join(item['brands'])} {' '.join(item['mcus'])}".lower()
        })
    
    app_index_file = os.path.join(DEST_DIR, "pinout_app_index.json")
    with open(app_index_file, "w", encoding="utf-8") as aif:
        json.dump(app_index, aif, indent=2, ensure_ascii=False)

    print(f"Extraction & Indexing complete! Total items indexed: {len(database)}")

if __name__ == "__main__":
    main()
