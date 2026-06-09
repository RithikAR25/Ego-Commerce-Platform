const fs = require('fs');
const http = require('http');

const BASE_URL = 'http://localhost:8080/api/v1';
const results = [];

function assert(condition, testId, message) {
    if (condition) {
        results.push({ id: testId, status: 'PASS', message });
        console.log(`✅ [PASS] ${testId}: ${message}`);
    } else {
        results.push({ id: testId, status: 'FAIL', message });
        console.error(`❌ [FAIL] ${testId}: ${message}`);
    }
}

function assertError(testId, error) {
    results.push({ id: testId, status: 'FAIL', message: error.message || error });
    console.error(`❌ [FAIL] ${testId}: ERROR: ${error.message}`);
}

async function fetchApi(path, options = {}) {
    try {
        const res = await fetch(`${BASE_URL}${path}`, options);
        let data = null;
        try {
            data = await res.json();
        } catch (e) {
            // Not JSON
        }
        return { status: res.status, data };
    } catch (e) {
        throw new Error(`Fetch failed for ${path}: ${e.message}`);
    }
}

async function runCategoryTests() {
    console.log('\n--- Running Category Architecture Tests ---');
    try {
        const { status, data: body } = await fetchApi('/categories');
        const roots = body?.data;

        // CAT-NAV-001
        assert(status === 200 && roots && roots.length === 5, 'CAT-NAV-001', `GET /categories returns ${roots?.length} ROOT categories`);

        // CAT-NAV-002 & CAT-NAV-003 & CAT-NAV-009
        let allRootsHaveGroups = true;
        let allGroupsHaveLeaves = true;
        let hasEmptyGroupWithNonNullLeaves = false;
        let menTopwearLeavesCount = 0;
        
        for (const root of roots || []) {
            if (!Array.isArray(root.groups)) allRootsHaveGroups = false;
            
            for (const group of root.groups || []) {
                if (!Array.isArray(group.leafCategories)) allGroupsHaveLeaves = false;
                if (Array.isArray(group.leafCategories) && group.leafCategories.length === 0) {
                    hasEmptyGroupWithNonNullLeaves = true; // CAT-NAV-009
                }
                if (root.slug === 'men' && group.slug === 'men-topwear') {
                    menTopwearLeavesCount = group.leafCategories.length;
                }
            }
        }
        assert(allRootsHaveGroups, 'CAT-NAV-002', 'Each ROOT has groups[]');
        assert(allGroupsHaveLeaves && menTopwearLeavesCount >= 8, 'CAT-NAV-003', `Each GROUP has leafCategories[] (Men/Topwear has ${menTopwearLeavesCount})`);
        assert(hasEmptyGroupWithNonNullLeaves, 'CAT-NAV-009', 'Empty Groups have leafCategories=[] not null');

        // CAT-NAV-004 & CAT-NAV-005
        let genZHoodiesInStreetwear = false;
        let genZHoodiesInOversized = false;
        let oversizedPrimary = true;

        const genZ = roots?.find(r => r.slug === 'gen-z');
        if (genZ) {
            // Actual slugs in DB: genz-streetwear / genz-oversized (no hyphen)
            const streetwear = genZ.groups.find(g => g.slug === 'genz-streetwear');
            const oversized  = genZ.groups.find(g => g.slug === 'genz-oversized');

            if (!streetwear) console.warn('⚠ genz-streetwear group not found in tree. Found groups:', genZ.groups.map(g => g.slug));
            if (!oversized)  console.warn('⚠ genz-oversized group not found in tree. Found groups:', genZ.groups.map(g => g.slug));

            const streetwearHoodies = streetwear?.leafCategories?.find(l => l.slug === 'genz-hoodies');
            if (streetwearHoodies) genZHoodiesInStreetwear = true;
            else console.warn('⚠ genz-hoodies not found under genz-streetwear. Found leaves:', streetwear?.leafCategories?.map(l => l.slug));

            const oversizedHoodies = oversized?.leafCategories?.find(l => l.slug === 'genz-hoodies');
            if (oversizedHoodies) {
                genZHoodiesInOversized = true;
                // API returns `primary` (not `isPrimary`) — false means secondary cross-listing
                oversizedPrimary = oversizedHoodies.primary;
            } else {
                console.warn('⚠ genz-hoodies not found under genz-oversized. Found leaves:', oversized?.leafCategories?.map(l => l.slug));
            }
        } else {
            console.warn('⚠ gen-z root not found. Found roots:', roots?.map(r => r.slug));
        }
        
        assert(genZHoodiesInStreetwear && genZHoodiesInOversized, 'CAT-NAV-004', 'Cross-listed category appears under multiple parents');
        assert(genZHoodiesInOversized && oversizedPrimary === false, 'CAT-NAV-005', 'Cross-listed leaf has primary=false on secondary link');

        // Leaves API
        const { status: lStatus, data: lBody } = await fetchApi('/categories/leaves');
        const leaves = lBody?.data || [];
        
        assert(lStatus === 200 && leaves.length >= 99, 'CAT-LEAF-001', `GET /categories/leaves returns ${leaves.length} LEAF categories`);
        
        let allLeavesHaveLevelLeaf = true;
        let hasParent = true;
        let hasRootOrGroup = false;
        
        for (const l of leaves) {
            if (l.level !== 'LEAF') allLeavesHaveLevelLeaf = false;
            if (!l.parent || !l.parent.id) hasParent = false;
            if (l.level === 'ROOT' || l.level === 'GROUP') hasRootOrGroup = true;
        }
        
        assert(allLeavesHaveLevelLeaf, 'CAT-LEAF-002', 'All returned categories have level="LEAF"');
        assert(!hasRootOrGroup, 'CAT-LEAF-003', 'ROOT and GROUP categories excluded from /leaves');
        assert(hasParent, 'CAT-LEAF-004', 'Each LEAF has parent{id, name, slug}');

        // By Slug API
        const { status: menStatus, data: menData } = await fetchApi('/categories/men');
        assert(menStatus === 200 && menData?.data?.level === 'ROOT', 'CAT-SLUG-001', 'GET /categories/men returns ROOT');

        const { status: grpStatus, data: grpData } = await fetchApi('/categories/men-topwear');
        assert(grpStatus === 200 && grpData?.data?.level === 'GROUP', 'CAT-SLUG-002', 'GET /categories/men-topwear returns GROUP');

        const { status: leafStatus, data: leafData } = await fetchApi('/categories/t-shirts');
        assert(leafStatus === 200 && leafData?.data?.level === 'LEAF', 'CAT-SLUG-003', 'GET /categories/t-shirts returns LEAF');

        const { status: invalidStatus } = await fetchApi('/categories/invalid-xyz');
        assert(invalidStatus === 404, 'CAT-SLUG-004', 'Inactive/Missing category returns 404');

        // Breadcrumbs API
        const { data: bMen } = await fetchApi('/categories/men/breadcrumbs');
        assert(bMen?.data?.length === 1 && bMen.data[0].slug === 'men', 'CAT-BRD-001', 'ROOT breadcrumb returns 1 item');

        const { data: bGrp } = await fetchApi('/categories/men-topwear/breadcrumbs');
        assert(bGrp?.data?.length === 2 && bGrp.data[1].slug === 'men-topwear', 'CAT-BRD-002', 'GROUP breadcrumb returns 2 items');

        const { data: bLeaf } = await fetchApi('/categories/t-shirts/breadcrumbs');
        assert(bLeaf?.data?.length === 3 && bLeaf.data[2].slug === 't-shirts', 'CAT-BRD-003', 'LEAF breadcrumb returns 3 items');

    } catch (e) {
        console.error('Test suite failed:', e);
    }
}

async function runAll() {
    await runCategoryTests();
    fs.writeFileSync('tests/api-results.json', JSON.stringify(results, null, 2));
    console.log('\nResults saved to tests/api-results.json');
}

runAll();
