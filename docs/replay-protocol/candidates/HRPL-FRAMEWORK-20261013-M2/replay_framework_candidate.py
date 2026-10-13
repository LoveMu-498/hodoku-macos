"""M2 standalone candidate codec/projection and verifier; no application integration.
Preserves original W1 fixture outcomes while validating profile 0.2 separately.
"""
import argparse
import base64
import copy
import hashlib
import json
import re
import struct
import zlib
import uuid
import os
import tempfile
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

LIMIT = 128 * 1024 * 1024
PROFILE = "HRPL-FRAME/0.1"
PROFILE_02 = "HRPL-FRAME/0.2"
PHASE_LABEL_LIMIT_02 = 16 * 1024 * 1024


def require(condition, message):
    if not condition:
        raise ValueError(message)


def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode("utf-8")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def integer(value, lo, hi):
    require(type(value) is int and lo <= value <= hi, "integer/range")


def text(value, identity=False, maximum_bytes=65535):
    require(type(value) is str, "string")
    require(len(value.encode("utf-8")) <= maximum_bytes, "text limit")
    if identity:
        require(bool(re.fullmatch(r"[\x21-\x7e]{1,128}", value)), "ID")


def fields(value, names):
    require(type(value) is dict and set(value) == set(names.split()), "fields: " + names)


def boolean(value):
    require(type(value) is bool, "boolean")


def color(value):
    require(type(value) is str and re.fullmatch(r"#[0-9A-Fa-f]{8}", value), "ARGB")


def digest(value):
    require(type(value) is str and re.fullmatch(r"[0-9a-f]{64}", value), "SHA-256")


def array(value, minimum, maximum):
    require(type(value) is list and minimum <= len(value) <= maximum, "array length")


def point(value):
    array(value, 2, 2)
    for v in value:
        integer(v, -500000, 1500000)


def bounds(value):
    array(value, 4, 4)
    point(value[:2]); point(value[2:])
    require(value[0] < value[2] and value[1] < value[3], "bounds")


def blob(value):
    require(type(value) is str and len(value) <= (8 * 1024 * 1024 + 2) // 3 * 4, "base64 limit")
    raw = base64.b64decode(value, validate=True)
    require(len(raw) <= 8 * 1024 * 1024, "blob limit")
    return raw


def frame_digest(frame):
    return sha(canonical({k: frame[k] for k in "id operationGroup phase phaseLabel wallTimeMs elapsedMs board display".split()}))


def png(raw):
    require(raw[:8] == b"\x89PNG\r\n\x1a\n", "PNG signature")
    pos, tags, compressed, dims = 8, [], bytearray(), None
    while pos < len(raw):
        require(pos + 12 <= len(raw), "PNG truncated")
        n = struct.unpack_from(">I", raw, pos)[0]
        require(n <= 8 * 1024 * 1024 and pos + 12 + n <= len(raw), "PNG chunk bound")
        tag, content = raw[pos+4:pos+8], raw[pos+8:pos+8+n]
        require(tag in (b"IHDR", b"IDAT", b"IEND"), "PNG profile chunk")
        require(zlib.crc32(tag + content) & 0xffffffff == struct.unpack_from(">I", raw, pos+8+n)[0], "PNG CRC")
        tags.append(tag)
        if tag == b"IHDR":
            require(len(tags) == 1 and n == 13, "PNG IHDR")
            w, h, depth, kind, comp, filt, inter = struct.unpack(">IIBBBBB", content)
            require(1 <= w <= 4096 and 1 <= h <= 4096 and w*h <= 16777216, "PNG dimensions")
            require((depth, kind, comp, filt, inter) == (8, 6, 0, 0, 0), "PNG RGBA8")
            dims = w, h
        elif tag == b"IDAT":
            require(b"IEND" not in tags and dims is not None, "PNG IDAT order")
            compressed.extend(content)
        else:
            require(n == 0 and pos+12 == len(raw), "PNG IEND")
        pos += n + 12
    require(tags and tags[-1] == b"IEND" and b"IDAT" in tags, "PNG completeness")
    w, h = dims
    expected = h * (1 + 4*w)
    inflater = zlib.decompressobj()
    scan = inflater.decompress(bytes(compressed), expected + 1)
    require(len(scan) == expected and inflater.eof and not inflater.unused_data and not inflater.unconsumed_tail, "PNG inflated bound")
    require(all(scan[y*(1+4*w)] in range(5) for y in range(h)), "PNG filters")


def validate(doc):
    fields(doc, "format session frames source losses")
    require(doc["format"] in (PROFILE, PROFILE_02), "profile")
    extended_label = doc["format"] == PROFILE_02
    fields(doc["session"], "id startedAtMs endedAtMs elapsedMs completed")
    text(doc["session"]["id"], True); boolean(doc["session"]["completed"])
    for k in ("startedAtMs", "endedAtMs", "elapsedMs"):
        integer(doc["session"][k], 0, 9007199254740991)
    array(doc["frames"], 1, 100000)
    ids, groups, previous_group, previous_time, notices = set(), set(), None, -1, []
    blob_records, extended_label_records = set(), set()
    for f in doc["frames"]:
        fields(f, "id operationGroup phase phaseLabel wallTimeMs elapsedMs board display native preview")
        text(f["id"], True); text(f["operationGroup"], True)
        text(f["phaseLabel"], maximum_bytes=PHASE_LABEL_LIMIT_02 if extended_label else 65535)
        if extended_label: extended_label_records.add(id(f))
        require(f["id"] not in ids, "duplicate frame ID"); ids.add(f["id"])
        if f["operationGroup"] != previous_group:
            require(f["operationGroup"] not in groups, "noncontiguous operation group")
            groups.add(f["operationGroup"]); previous_group = f["operationGroup"]
        require(f["phase"] in ("input", "evidence", "result", "applied", "other"), "phase")
        integer(f["wallTimeMs"], 0, 9007199254740991); integer(f["elapsedMs"], 0, 9007199254740991)
        require(f["elapsedMs"] >= previous_time, "elapsed order"); previous_time = f["elapsedMs"]
        fields(f["board"], "values fixed candidates userCandidates")
        for k, values in f["board"].items():
            array(values, 81, 81)
            for v in values:
                boolean(v) if k == "fixed" else integer(v, 0, 9 if k == "values" else 511)
        for i, v in enumerate(f["board"]["values"]):
            require(not f["board"]["fixed"][i] or v != 0, "empty given")
            require(v == 0 or f["board"]["candidates"][i] == 0, "filled candidates")
        array(f["display"], 0, 10000)
        object_ids, partial = set(), False
        for o in f["display"]:
            require(type(o) is dict, "object")
            for k in ("id", "type"): text(o.get(k), True)
            boolean(o.get("required")); boolean(o.get("important")); integer(o.get("z"), -32768, 32767)
            require(o["id"] not in object_ids, "duplicate object ID"); object_ids.add(o["id"])
            common = "id type required important z group "
            additions = {"mark": "shape bounds anchor color width initial conclusion outline", "highlight": "bounds anchor fill", "line": "points color width dash arrow", "region": "points fill stroke width", "text": "at text color fontSize align"}
            if o["type"] not in additions:
                require(not o["required"], "unknown required object")
                # Still reject invalid JSON values/text in unknown optional records below.
                partial |= o["important"]
                notices.append("UNKNOWN_OPTIONAL:" + o["id"])
                continue
            fields(o, common + additions[o["type"]])
            if o["group"] is not None: text(o["group"], True)
            if "bounds" in o: bounds(o["bounds"])
            if "anchor" in o and o["anchor"] is not None:
                fields(o["anchor"], "cell digit"); integer(o["anchor"]["cell"], 0, 80); integer(o["anchor"]["digit"], 0, 9)
            for k in ("color", "fill", "stroke"):
                if k in o: color(o[k])
            if "width" in o: integer(o["width"], 1, 100000)
            if "points" in o:
                array(o["points"], 3 if o["type"] == "region" else 2, 4096)
                for p in o["points"]: point(p)
            if o["type"] == "mark":
                require(o["shape"] in ("circle", "cross"), "mark shape")
                boolean(o["initial"]); boolean(o["conclusion"])
                if o["outline"] is not None:
                    fields(o["outline"], "color width gap"); color(o["outline"]["color"])
                    integer(o["outline"]["width"], 1, 100000); integer(o["outline"]["gap"], 0, 100000)
            if o["type"] == "line":
                require(o["dash"] in ("solid", "dashed"), "dash"); require(o["arrow"] in ("none", "start", "end", "both"), "arrow")
                require(any(a != b for a,b in zip(o["points"],o["points"][1:])), "zero length line")
            if o["type"] == "text":
                point(o["at"]); text(o["text"]); integer(o["fontSize"], 1000, 500000)
                require(o["align"] in ("left", "center", "right"), "text align")
        binding = frame_digest(f)
        array(f["native"], 0, 256)
        namespaces = set()
        for n in f["native"]:
            fields(n, "namespace version binding data"); text(n["namespace"], True); integer(n["version"], 1, 65535)
            require(n["namespace"] not in namespaces, "native namespace duplicate"); namespaces.add(n["namespace"])
            digest(n["binding"]); require(n["binding"] == binding, "stale native binding"); blob(n["data"])
            blob_records.add(id(n))
        if f["preview"] is not None:
            p = f["preview"]; fields(p, "mime coverage binding data")
            require(p["mime"] == "image/png" and p["coverage"] == "full-frame", "preview profile")
            digest(p["binding"]); require(p["binding"] == binding, "stale preview binding"); png(blob(p["data"]))
            blob_records.add(id(p))
        if partial: notices.append("PREVIEW_FALLBACK" if f["preview"] is not None else "PARTIAL_VIEW")
    require(doc["session"]["elapsedMs"] >= previous_time, "session elapsed")
    if doc["source"] is not None:
        fields(doc["source"], "fileSha256 frameIds"); digest(doc["source"]["fileSha256"])
        array(doc["source"]["frameIds"], 1, 100000)
        for v in doc["source"]["frameIds"]: text(v, True)
    array(doc["losses"], 0, 100000)
    for loss in doc["losses"]:
        fields(loss, "frameId namespace reason")
        text(loss["frameId"], True); text(loss["namespace"], True); text(loss["reason"])
    # Global JSON scalar limits also apply to unknown optional objects.
    def walk(v, depth=0):
        require(depth <= 64, "JSON nesting limit")
        if isinstance(v, dict):
            for k, x in v.items():
                text(k)
                if k == "data" and id(v) in blob_records: continue
                if k == "phaseLabel" and id(v) in extended_label_records: continue
                walk(x, depth+1)
        elif isinstance(v, list):
            for x in v: walk(x, depth+1)
        elif type(v) is str: text(v)
        elif type(v) is int: integer(v, -9007199254740991, 9007199254740991)
        else: require(v is None or type(v) is bool, "JSON scalar")
    walk(doc)
    return notices


def encode(doc):
    validate(doc)
    payload = canonical(doc)
    require(len(payload) + 12 <= LIMIT, "file limit")
    return struct.pack(">III", 0x4852504C, 4, len(payload)) + payload


def decode(raw):
    require(12 <= len(raw) <= LIMIT, "file bound")
    magic, version, length = struct.unpack_from(">III", raw)
    require(magic == 0x4852504C and version == 4, "magic/version")
    require(length <= LIMIT-12 and length == len(raw)-12, "payload length/EOF")
    def unique(pairs):
        out = {}
        for k, v in pairs:
            require(k not in out, "duplicate JSON key"); out[k] = v
        return out
    try:
        doc = json.loads(raw[12:].decode("utf-8"), object_pairs_hook=unique)
    except RecursionError as error:
        raise ValueError("JSON nesting limit") from error
    return doc, validate(doc)


def direct_save(raw):
    decode(raw)
    return bytes(raw)


def write_document(path, doc):
    raw=encode(doc) # validate before touching the destination
    temporary=None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent,delete=False) as stream:
            temporary=Path(stream.name); stream.write(raw)
        os.replace(temporary,path)
    finally:
        if temporary is not None and temporary.exists(): temporary.unlink()


def modified_copy(doc, raw, new_identity):
    require(new_identity != doc["session"]["id"], "modified session must have new identity")
    result=copy.deepcopy(doc)
    result["session"]["id"]=new_identity
    result["source"]=dict(fileSha256=sha(raw),frameIds=[f["id"] for f in doc["frames"]])
    for i,f in enumerate(result["frames"]):
        f["id"]=new_identity+"-f"+str(i)
        for n in f["native"]:
            result["losses"].append(dict(frameId=f["id"],namespace=n["namespace"],reason="stale-binding"))
        if f["preview"] is not None:
            result["losses"].append(dict(frameId=f["id"],namespace="preview",reason="stale-binding"))
        f["native"]=[]; f["preview"]=None
    validate(result)
    return result,bytes(raw) # caller must persist archive before integration/export


def project_o11(overlay, board_side_px, stroke_px, outline_px, group_by_index=None, frozen_results=None):
    """Caller supplies captured styles/groups/results. Never run a solver or infer groups from ARGB."""
    require(board_side_px > 0 and stroke_px > 0 and outline_px > 0, "capture style")
    def unit(v):
        d = Decimal(str(v)); require(d.is_finite(), "finite geometry")
        return int((d * 1000000).to_integral_value(rounding=ROUND_HALF_UP))
    display, notices = [], []
    for line in overlay.splitlines():
        t = line.split()
        if not t: continue
        if t[0] == "MT":
            require(t == ["MT", "1"], "MT layout")
            if frozen_results is None: notices.append("PARTIAL_VIEW:MT_WITHOUT_CAPTURED_RESULTS")
        elif t[0] == "O":
            require(len(t) == 11, "only exact O11 projection supported")
            rgb, kind, cell, digit, conclusion, initial = map(int, t[1:7])
            integer(rgb, -2147483648, 2147483647); integer(kind, 0, 2)
            require(conclusion in (0, 1) and initial in (0, 1), "O11 flags")
            require((cell, digit) == (-1, -1) or (0 <= cell <= 80 and 1 <= digit <= 9), "O11 anchor")
            x1, y1, x2, y2 = map(unit, t[7:])
            x1, x2 = sorted((x1, x2)); y1, y2 = sorted((y1, y2))
            if kind == 2:
                # Native cross endpoints occupy .62 of the smaller original box side.
                cx, cy = (x1+x2)//2, (y1+y2)//2
                r = int((Decimal(min(x2-x1, y2-y1)) * Decimal("0.31")).to_integral_value(rounding=ROUND_HALF_UP))
                x1, y1, x2, y2 = cx-r, cy-r, cx+r, cy+r
            i = len(display)
            display.append(dict(id="o11-"+str(i), type="mark", required=False, important=True, z=i,
                shape="cross" if kind == 2 else "circle", bounds=[x1,y1,x2,y2],
                anchor=None if cell == -1 else dict(cell=cell,digit=digit), color="#%08X" % (rgb & 0xffffffff),
                width=unit((stroke_px+initial)/board_side_px), initial=bool(initial), conclusion=bool(conclusion),
                outline=dict(color="#FF1769FF",width=unit(outline_px/board_side_px),gap=0) if conclusion else None,
                group=(group_by_index or {}).get(i)))
        else:
            notices.append("UNPROJECTED_LEGACY:"+t[0])
    if frozen_results is not None: display.extend(copy.deepcopy(frozen_results))
    return display, notices


def continue_snapshot(doc, raw, index, recorded_ms):
    """Preparation only: returns new inert document and immutable source bytes for the caller to archive."""
    old = doc["frames"][index]
    frame = copy.deepcopy(old)
    identity=str(uuid.uuid4())
    frame.update(id=identity+"-f0", operationGroup=identity+"-op0", phase="input", phaseLabel="", wallTimeMs=recorded_ms,elapsedMs=0,
                 display=[], native=[], preview=None)
    branch = dict(format=PROFILE, session=dict(id=identity,startedAtMs=recorded_ms,endedAtMs=0,elapsedMs=0,completed=False),
                  frames=[frame], source=dict(fileSha256=sha(raw),frameIds=[old["id"]]),losses=[])
    validate(branch)
    return branch, bytes(raw)


def make_png():
    def chunk(tag, data):
        return struct.pack(">I",len(data))+tag+data+struct.pack(">I",zlib.crc32(tag+data)&0xffffffff)
    return b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",1,1,8,6,0,0,0))+chunk(b"IDAT",zlib.compress(b"\0\0\0\0\0"))+chunk(b"IEND",b"")


def batch(destination):
    destination.mkdir(parents=True, exist_ok=True)
    board = dict(values=[0]*81, fixed=[False]*81, candidates=[511]*81, userCandidates=[0]*81)
    board["values"][0]=5; board["fixed"][0]=True; board["candidates"][0]=0; board["userCandidates"][2]=8
    overlay="O -65536 1 2 4 0 1 0.1 0.2 0.2 0.3\nO -16776961 2 3 7 1 0 0.3 0.2 0.4 0.3\nO -16777216 0 -1 -1 0 0 0.5 0.5 0.6 0.6\nMT 1\n"
    common=dict(required=False,important=True,z=10,group="group-A")
    frozen=[dict(common,id="frozen-result",type="highlight",bounds=[222222,0,333333,111111],anchor=dict(cell=2,digit=4),fill="#6600CCFF"),
            dict(common,id="frozen-text",type="text",at=[0,1100000],text="冻结结果／frozen result: 4; no coverage proof",color="#FF000000",fontSize=20000,align="left")]
    marks, _ = project_o11(overlay,900,3,2, {0:"group-A",1:"group-B"}, frozen)
    marks += [dict(common,id="line-0",type="line",points=[[100000,100000],[500000,500000]],color="#FF00AA00",width=3000,dash="dashed",arrow="end"),
              dict(common,id="region-0",type="region",points=[[0,0],[333333,0],[333333,333333],[0,333333]],fill="#330000FF",stroke="#FF0000FF",width=2000)]
    frames=[]
    for i, phase in enumerate(("input","evidence","applied")):
        f=dict(id="frame-"+str(i),operationGroup="op-0" if i<2 else "op-1",phase=phase,phaseLabel="",wallTimeMs=1000+i*100,elapsedMs=i*100,
               board=copy.deepcopy(board),display=copy.deepcopy(marks) if i==1 else [],native=[],preview=None)
        if i==2:
            f["board"]["values"][2]=4; f["board"]["candidates"][2]=0
        frames.append(f)
    frames[1]["native"]=[dict(namespace="example.windows",version=1,binding=frame_digest(frames[1]),data=base64.b64encode(b"opaque synthetic native\0").decode())]
    doc=dict(format=PROFILE,session=dict(id="synthetic-framework-w1",startedAtMs=1000,endedAtMs=1200,elapsedMs=200,completed=False),frames=frames,source=None,losses=[])
    noncanonical=json.dumps(doc,ensure_ascii=False,indent=2).encode("utf-8")
    cases={"all-content.hrep":(encode(doc),"PASS"),"noncanonical.hrep":(struct.pack(">III",0x4852504C,4,len(noncanonical))+noncanonical,"PASS")}
    optional=copy.deepcopy(doc); optional["frames"][0]["display"]=[dict(id="future",type="example.future",required=False,important=True,z=0,payload="opaque")]
    cases["unknown-important.hrep"]=(encode(optional),"PARTIAL_VIEW")
    preview=copy.deepcopy(optional)
    preview["frames"][0]["preview"]=dict(mime="image/png",coverage="full-frame",binding=frame_digest(preview["frames"][0]),data=base64.b64encode(make_png()).decode())
    cases["preview-carrier.hrep"]=(encode(preview),"PREVIEW_FALLBACK") # transport only, not fidelity acceptance
    def unvalidated(d):
        p=canonical(d); return struct.pack(">III",0x4852504C,4,len(p))+p
    required=copy.deepcopy(optional); required["frames"][0]["display"][0]["required"]=True
    bad=copy.deepcopy(doc); bad["frames"][1]["display"][0]["bounds"][0]=-500001
    stale=copy.deepcopy(doc); stale["frames"][1]["board"]["userCandidates"][2]=0
    invalid_board=copy.deepcopy(doc); invalid_board["frames"][0]["board"]["candidates"][3]=512
    bad_time=copy.deepcopy(doc); bad_time["frames"][2]["elapsedMs"]=99
    bad_png=copy.deepcopy(preview); broken=bytearray(make_png()); broken[-1]^=1
    bad_png["frames"][0]["preview"]["data"]=base64.b64encode(broken).decode()
    too_long=copy.deepcopy(doc); too_long["frames"][0]["phaseLabel"]="中"*21846
    invalid_native=copy.deepcopy(doc); invalid_native["frames"][1]["native"][0]["data"]="!notbase64!"
    group_return=copy.deepcopy(doc); group_return["frames"][1]["operationGroup"]="op-1"; group_return["frames"][2]["operationGroup"]="op-0"
    noninteger=copy.deepcopy(doc); noninteger["frames"][0]["elapsedMs"]=0.5
    duplicate=canonical(doc).replace(b'"format":"HRPL-FRAME/0.1"', b'"format":"HRPL-FRAME/0.1","format":"HRPL-FRAME/0.1"')
    cases.update({"unknown-required.hrep":(unvalidated(required),"FAIL"),"invalid-coordinate.hrep":(unvalidated(bad),"FAIL"),
        "stale-binding.hrep":(unvalidated(stale),"FAIL"),"duplicate-key.hrep":(struct.pack(">III",0x4852504C,4,len(duplicate))+duplicate,"FAIL"),
        "truncated.hrep":(encode(doc)[:-1],"FAIL"),"oversize-declared.hrep":(struct.pack(">III",0x4852504C,4,LIMIT),"FAIL"),
        "wrong-version.hrep":(struct.pack(">III",0x4852504C,99,0),"FAIL"),"invalid-board.hrep":(unvalidated(invalid_board),"FAIL"),
        "time-order.hrep":(unvalidated(bad_time),"FAIL"),"invalid-preview.hrep":(unvalidated(bad_png),"FAIL"),
        "unicode-overlimit.hrep":(unvalidated(too_long),"FAIL"),"invalid-native.hrep":(unvalidated(invalid_native),"FAIL"),
        "group-return.hrep":(unvalidated(group_return),"FAIL"),"noninteger.hrep":(unvalidated(noninteger),"FAIL")})
    expected={}
    for name,(raw,result) in cases.items():
        (destination/name).write_bytes(raw)
        expected[name]=dict(sha256=sha(raw),bytes=len(raw),expected=result)
    (destination/"expected.json").write_bytes(canonical(expected)+b"\n")
    (destination/"all-content.json").write_bytes(canonical(doc)+b"\n")
    return expected


def verify(destination, report_path=None):
    expected=json.loads((destination/"expected.json").read_text("utf-8"))
    rows=[]
    for name, item in expected.items():
        raw=(destination/name).read_bytes()
        require(sha(raw)==item["sha256"] and len(raw)==item["bytes"],"fixture hash")
        try:
            doc, notices=decode(raw)
            result="PREVIEW_FALLBACK" if "PREVIEW_FALLBACK" in notices else "PARTIAL_VIEW" if "PARTIAL_VIEW" in notices else "PASS"
        except (ValueError, UnicodeError, struct.error, zlib.error): result="FAIL"
        require(result==item["expected"],name+": expected "+item["expected"]+", got "+result)
        rows.append(dict(case=name,expected=result,verified=True))
    raw=(destination/"all-content.hrep").read_bytes(); doc,_=decode(raw)
    require(encode(doc)==raw,"canonical byte round trip")
    noncanonical=(destination/"noncanonical.hrep").read_bytes()
    require(direct_save(noncanonical)==noncanonical,"direct save preserves noncanonical bytes and native")
    branch, archive=continue_snapshot(doc,raw,1,2000)
    require(archive==raw and branch["frames"][0]["board"]==doc["frames"][1]["board"],"branch snapshot/archive")
    require(not branch["frames"][0]["display"] and not branch["frames"][0]["native"],"branch inert history")
    edited, archived=modified_copy(doc,raw,"edited-synthetic")
    require(archived==raw and not edited["frames"][1]["native"] and edited["losses"][0]["reason"]=="stale-binding","edited archive/loss")
    projected, notices=project_o11("O -65536 1 2 4 0 1 0.1 0.2 0.2 0.3\nMT 1\n",900,3,2)
    require(projected[0]["group"] is None and "PARTIAL_VIEW:MT_WITHOUT_CAPTURED_RESULTS" in notices,"no color inferred group or recomputed MT")
    temporary_probe=tempfile.TemporaryDirectory(prefix="hrpl-m2-probe-")
    protected=Path(temporary_probe.name)/"write-protection.bin"; protected.write_bytes(b"previous")
    invalid=copy.deepcopy(doc); invalid["frames"][0]["board"]["values"][0]=10
    try: write_document(protected,invalid); raise AssertionError("invalid write accepted")
    except ValueError: require(protected.read_bytes()==b"previous","failed write changed file")
    temporary_probe.cleanup()
    report=dict(codec=rows,canonicalRoundTrip="PASS",unchangedByteCopy="PASS",editArchivePrototype="PASS",branchPrototype="PASS",o11ProjectionPreparation="PASS",writeProtection="PASS",applicationIntegration="NOT_RUN",gui="NOT_RUN",previewFidelity="NOT_RUN")
    if report_path is not None:
        report_path.parent.mkdir(parents=True,exist_ok=True)
        report_path.write_bytes(canonical(report)+b"\n")
    print("PASS:",len(rows),"fixture outcomes; canonical round trip; isolated snapshot continuation")



def verify_file(path):
    raw=path.read_bytes()
    doc,notices=decode(raw)
    require(direct_save(raw)==raw,"file direct-save preservation")
    roundtrip,again=decode(encode(doc))
    require(roundtrip==doc and again==notices,"file canonical semantic round trip")
    result=dict(file=path.name,profile=doc["format"],frames=len(doc["frames"]),sha256=sha(raw),bytes=len(raw),notices=notices)
    print("PASS FILE:",json.dumps(result,ensure_ascii=False))
    return result


def self_test_m2(destination, report_path=None):
    baseline,_=decode((destination/"all-content.hrep").read_bytes())
    rows=[]
    def expect(name,doc,accepted):
        try:
            raw=encode(doc)
            decoded,_=decode(raw)
            require(decoded==doc,"semantic round trip")
            actual=True
        except (ValueError,UnicodeError,RecursionError,TypeError):
            actual=False
        require(actual==accepted,name)
        rows.append(dict(case=name,result="PASS" if accepted else "REJECT"))
    doc=copy.deepcopy(baseline)
    obj=dict(id="future",type="example.future",required=False,important=False,z=0,namespace="synthetic",binding="not-a-native-binding",data=1.5)
    doc["frames"][0]["display"].append(obj)
    expect("unknown-data-float",doc,False)
    obj["data"]="x"*65536; expect("unknown-data-overlimit",doc,False)
    obj["data"]="x"*65535; expect("unknown-data-boundary",doc,True)
    doc=copy.deepcopy(baseline)
    doc["frames"][0]["phaseLabel"]="中"*21846
    expect("profile01-long-label-rejected",doc,False)
    doc["format"]=PROFILE_02
    expect("profile02-unicode-label",doc,True)
    doc["frames"][0]["phaseLabel"]="中"*(PHASE_LABEL_LIMIT_02//3)+"x"*(PHASE_LABEL_LIMIT_02%3)
    expect("profile02-exact16MiB-unicode",doc,True)
    doc["frames"][0]["phaseLabel"]+="x"
    expect("profile02-over16MiB",doc,False)
    doc["frames"][0]["phaseLabel"]="x"*PHASE_LABEL_LIMIT_02
    expect("profile02-exact16MiB-ascii",doc,True)
    doc["frames"][0]["phaseLabel"]="valid"
    obj=dict(id="future-label",type="example.future",required=False,important=False,z=0,phaseLabel="x"*65536)
    doc["frames"][0]["display"].append(obj)
    expect("profile02-unknown-phaseLabel-no-exemption",doc,False)
    obj["phaseLabel"]="valid";obj["text"]="x"*65536
    expect("profile02-other-text-bound-unchanged",doc,False)
    report=dict(packageId="HRPL-FRAMEWORK-20261013-M2",cases=rows,scope="standalone codec only; no GUI or application claim")
    if report_path is not None:
        report_path.parent.mkdir(parents=True,exist_ok=True)
        report_path.write_bytes(canonical(report)+b"\n")
    print("PASS M2:",len(rows),"bounded profile and validated-record exemption cases")
    return report


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("action",choices=["generate","verify","verify-file","self-test"])
    parser.add_argument("directory",type=Path)
    parser.add_argument("--report",type=Path)
    args=parser.parse_args()
    if args.action=="generate": batch(args.directory)
    elif args.action=="verify": verify(args.directory,args.report)
    elif args.action=="verify-file": verify_file(args.directory)
    else: self_test_m2(args.directory,args.report)
