/**
 * Handles Form Submission and API Integration
 * Developer: Kalyan king
 */

async function handleFormSubmit(event) {
    event.preventDefault();

    const inputUrl = document.getElementById('profileUrl').value.trim();
    const loader = document.getElementById('loader');
    const resultBox = document.getElementById('resultBox');
    const profileImage = document.getElementById('profileImage');
    const downloadBtn = document.getElementById('downloadBtn');

    if (!inputUrl) {
        alert('অনুগ্রহ করে একটি বৈধ Facebook URL প্রদান করুন!');
        return;
    }

    // Reset UI state
    resultBox.style.display = 'none';
    loader.style.display = 'block';

    // Target API URL
    const targetApi = `http://dark-knight.tech/api.php?url=${encodeURIComponent(inputUrl)}`;

    try {
        const response = await fetch(targetApi);

        if (!response.ok) {
            throw new Error('API রিকোয়েস্টে সমস্যা হয়েছে');
        }

        const data = await response.json();
        
        // API response validation
        const finalImageUrl = data.url || data.image_url || data.result || targetApi;

        displayResult(finalImageUrl);

    } catch (error) {
        console.warn('Direct JSON parsing failed, loading directly via URL:', error);
        // Fallback strategy for direct image response or CORS blocking
        displayResult(targetApi);
    }
}

/**
 * Renders the fetched image to the DOM
 * @param {string} imageUrl 
 */
function displayResult(imageUrl) {
    const loader = document.getElementById('loader');
    const resultBox = document.getElementById('resultBox');
    const profileImage = document.getElementById('profileImage');
    const downloadBtn = document.getElementById('downloadBtn');

    profileImage.src = imageUrl;
    downloadBtn.href = imageUrl;

    // Show output section
    loader.style.display = 'none';
    resultBox.style.display = 'block';
}
