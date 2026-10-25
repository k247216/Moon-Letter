# Offline geographic map

`china-provinces.geojson` is the unmodified published GeoJSON downloaded with
PowerShell Invoke-WebRequest on 2026-10-02 from Alibaba Cloud DataV GeoAtlas:

https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json

Publisher viewer: https://datav.aliyun.com/portal/school/atlas/area_selector

The asset contains the publisher's real province polygons and coastline, including
islands and its supplied boundary line features. Boundaries were not hand drawn,
simplified, or invented by Moon Letter. Source attribution remains visible on map.
The endpoint publishes no license declaration in the downloaded JSON; attribution
does not assert an open license or approval for surveyed/cartographic use.

The map uses native Compose Canvas and a Web Mercator display projection. City
coordinates in CityCatalog are approximate city-centre anchors, not live positions.
This is a provincial/city geographic base, without streets, terrain, or POI tiles. No
network, geolocation, WebView, Javascript bridge, map key, tile cache or prefetch is
used for the default offline provincial layer. Zoom reveals the original dataset detail, not extra street data.

Optional street mode is explicitly opened by the user through “地图样式 → 街道地图”.
It uses locally packaged Leaflet 1.9.4 (BSD-2-Clause, see Leaflet-LICENSE.txt) and
OpenStreetMap's public HTTPS tile service for this limited test build. Visible
OSM attribution, identified app User-Agent, normal WebView HTTP cache, no tile
prefetch or offline tile download. Only current viewport tiles are requested;
no story text, photos or user/couple identifiers are sent. Service availability
in mainland China is not guaranteed. The independent offline geographic layer
remains usable when the network is unavailable. Production distribution needs
a reviewed domestic map source/provider contract; no Amap/Tianditu key is bundled.

OSMF policy: https://operations.osmfoundation.org/policies/tiles/
Leaflet source: https://unpkg.com/leaflet@1.9.4/dist/

## City boundaries and catalog expansion (fetched 2026-10-02)

`china-cities.geojson` mechanically concatenates all 363 original feature objects
from these 27 Alibaba Cloud DataV GeoAtlas province-level responses:

`https://geo.datav.aliyun.com/areas_v3/bound/{adcode}_full.json`

Adcodes (in merge order): 130000, 140000, 150000, 210000, 220000, 230000,
320000, 330000, 340000, 350000, 360000, 370000, 410000, 420000, 430000,
440000, 450000, 460000, 510000, 520000, 530000, 540000, 610000, 620000,
630000, 640000, 650000.

The province list was obtained by downloading the national endpoint above on
2026-10-02. Beijing, Tianjin, Shanghai and Chongqing are represented by the
existing province asset and legacy catalog entries; their district-level
`_full.json` responses were not used as cities. Taiwan, Hong Kong and Macau retain
their existing catalog anchors and original national-asset boundaries; no city
subdivision boundaries were invented for them.

All 363 input features have `properties.level = "city"`. This is the publisher's
classification: it includes prefecture cities, autonomous prefectures, leagues,
regions and province-administered county-level units. It should not be interpreted
as a claim that all 363 units are administratively prefecture-level cities.
Every feature object's original JSON text, geometry, coordinates, properties,
islands and supplied boundary details was preserved verbatim. Only the enclosing
FeatureCollection was created to combine them. There was no simplification,
clipping, redrawing, disputed-boundary alteration or coordinate transformation.
The existing `china-provinces.geojson` was not modified (SHA-256:
`99ADFEDED5223848BBE37A0A12F8023E11EE12161C7800521C27DB42FDEAC275`).

`DataVCities.kt` derives its 363 entries from these features' `properties.name`
and `properties.center` (source order is longitude, latitude). Only a terminal
city suffix `市` is removed; autonomous-prefecture and other names remain intact.
Province labels follow the existing short-name style. `CityCatalog.majorCities`
keeps all original 44 entries first, with unchanged names and coordinates, then
adds new names from this list, producing 371 unique selections. The legacy `大理`
selection remains alongside the source's `大理白族自治州`; these are separate
catalog anchors, not a fabricated extra boundary.

`CityCatalog.nearest` measures spherical Haversine distance to catalog centres
with mean Earth radius 6371.0088 km and a default maximum distance of 120 km.
It is a proximity lookup, not point-in-polygon containment, an administrative
border check or a geocoding/location service. The source does not declare a
coordinate reference system; no additional datum guarantee is asserted here.

Official interface documentation:
https://help.aliyun.com/zh/datav/datav-6-0/user-guide/choropleth-layer-of-v3-x

The public JSON interfaces and official usage documentation do not constitute
a formal commercial redistribution license. No explicit license declaration was
present in these downloaded responses. Attribution alone does not grant
commercial use, map-publication approval or surveying/cartography permission;
production use requires independent rights and provider-contract review.
